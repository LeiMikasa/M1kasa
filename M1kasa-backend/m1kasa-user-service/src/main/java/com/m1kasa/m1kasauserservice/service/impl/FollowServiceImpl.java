package com.m1kasa.m1kasauserservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.m1kasauserservice.cache.FollowCacheInitializer;
import com.m1kasa.m1kasauserservice.constant.FollowRedisKeys;
import com.m1kasa.m1kasauserservice.entity.Follow;
import com.m1kasa.m1kasauserservice.entity.User;
import com.m1kasa.m1kasauserservice.mapper.FollowMapper;
import com.m1kasa.m1kasauserservice.mapper.UserMapper;
import com.m1kasa.m1kasauserservice.service.FollowService;
import com.m1kasa.m1kasauserservice.service.UserService;
import com.m1kasa.user.api.FollowPageDTO;
import com.m1kasa.user.api.FollowStateDTO;
import com.m1kasa.user.api.FollowUserDTO;
import com.m1kasa.user.api.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FollowServiceImpl implements FollowService {
    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FollowCacheInitializer initializer;
    private final UserService userService;
    public FollowServiceImpl(UserMapper userMapper, FollowMapper followMapper, StringRedisTemplate stringRedisTemplate, FollowCacheInitializer initializer, UserService userService) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.initializer = initializer;
        this.userService = userService;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FollowStateDTO setFollowed(Long targetUserId, boolean followed) {
         Long userId=requireCurrentUserId();
         validateTargetUserId(targetUserId);
         if (userId.equals(targetUserId)){
             throw new IllegalArgumentException("不能关注自己");
         }
         //1.与缓存初始化使用相同的用户行锁
         Long lockedUserId = userMapper.selectIdForUpdate(userId);
         if (lockedUserId==null){
             throw new IllegalStateException("当前用户不存在");
         }
        LambdaQueryWrapper<Follow> relationQuery =
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, userId)
                        .eq(Follow::getFollowUserId, targetUserId);
         if (followed) {
             //2.关注前，检查目标作者是否存在
             boolean targetExists = userMapper.exists(
                     new LambdaQueryWrapper<User>()
                             .eq(User::getId, targetUserId)
             );
             if (!targetExists) {
                 throw new IllegalArgumentException("目标用户不存在");
             }
             //已关注时不重复插入
             if (!followMapper.exists(relationQuery)) {
                 Follow relation = new Follow();
                 relation.setUserId(userId);
                 relation.setFollowUserId(targetUserId);
                 relation.setCreateTime(LocalDateTime.now());

                 if (followMapper.insert(relation) != 1) {
                     throw new IllegalStateException("保存关注关系失败");
                 }

             }
         }else {
                 //没有记录时删除0行，也视为取消成功
                 followMapper.delete(relationQuery);
         }
         // 3. 持有用户行锁期间删除缓存，然后由 Spring 提交事务
         // Redis 异常直接向外抛出，让本次数据库事务回滚
        stringRedisTemplate.delete(
                FollowRedisKeys.following(userId)
        );
         return new FollowStateDTO(
                 targetUserId.toString(),
                 followed
         );

    }

    @Override
    public FollowStateDTO getFollowState(Long targetUserId) {
        Long userId=requireCurrentUserId();
        validateTargetUserId(targetUserId);
        //查询自己时，返回未关注
        if (userId.equals(targetUserId)){
            return new FollowStateDTO(
                    targetUserId.toString(),
                    false
            );
        }
        //1.先读缓存
        Boolean followed=readCachedState(userId,targetUserId);
        if (followed!=null){
            return new FollowStateDTO(
              targetUserId.toString(),
              followed
            );
        }
        //2.未初始化时，加载完整关注列表
        initializer.initialize(userId);
        //3.初始化后再次读取缓存
        followed=readCachedState(userId,targetUserId);
        // 4. 缓存可能刚好被关注操作删除，回查数据库
        if (followed == null) {
            followed = followMapper.exists(
                    new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, userId)
                            .eq(Follow::getFollowUserId, targetUserId)
            );
        }

        return new FollowStateDTO(
                targetUserId.toString(),
                followed
        );
    }

    @Override
    public FollowPageDTO findFollowing(int current, int size) {
        return findRelationPage(current, size, true);
    }

    @Override
    public FollowPageDTO findFollowers(int current, int size) {
        return findRelationPage(current, size, false);
    }

    /**
     * true 查询我关注的人  false 查询关注我的人
     * @param current
     * @param size
     * @param followingList
     * @return
     */
    private FollowPageDTO findRelationPage(
            int current,
            int size,
            boolean followingList
    ){
        Long userId = requireCurrentUserId();
        //1.校验分页参数
        if (current<1||size<1||size>50){
            throw new IllegalArgumentException("页码必须大于0，每页条数必须在1到50之间");
        }
        //2.根据列表类型确定查询方向
        LambdaQueryWrapper<Follow> queryWrapper=
                new LambdaQueryWrapper<Follow>()
                        .select(
                                Follow::getId,
                                Follow::getUserId,
                                Follow::getFollowUserId,
                                Follow::getCreateTime
                        );
        if (followingList){
            queryWrapper.eq(Follow::getUserId,userId);
        }else {
            queryWrapper.eq(Follow::getFollowUserId,userId);
        }
        //关注时间相同，再按记录ID倒序排列
        queryWrapper.orderByDesc(Follow::getCreateTime,Follow::getId);
        Page<Follow> page=new Page<>(current,size);
        followMapper.selectPage(page,queryWrapper);
        List<Follow> relations = page.getRecords();
        //3.当前页没有关系记录直接返回
        if (relations.isEmpty()){
            return new FollowPageDTO(
                    List.of(),
                    page.getTotal(),
                    page.getCurrent(),
                    page.getSize()
            );
        }
        //4.收集当前页需要展示的用户ID
        List<Long> listedUserIds=new ArrayList<>();
        for (Follow relation : relations) {
            Long listedUserId=followingList?relation.getFollowUserId():relation.getUserId();

            if (listedUserId == null || listedUserId <= 0) {
                throw new IllegalStateException("数据库关注关系不合法");
            }

            listedUserIds.add(listedUserId);
        }
        // 5. 批量获取当前页用户资料，头像已转换成访问链接
        List<UserDTO> users = userService.listByIds(listedUserIds);

        Map<Long, UserDTO> usersById = new HashMap<>();

        for (UserDTO user : users) {
            usersById.put(user.id(), user);
        }
        //6.确定当前用户关注了列表中的哪些人
        Set<Long> followedUserIds=new HashSet<>();
        if (followingList){
            //关注列表中的人都是当前用户已关注的人
            followedUserIds.addAll(listedUserIds);
        }else {
            //粉丝列表,批量查询当前用户是否回关
            List<Follow> myRelations = followMapper.selectList(
                    new LambdaQueryWrapper<Follow>()
                            .select(Follow::getFollowUserId)
                            .eq(Follow::getUserId, userId)
                            .in(Follow::getFollowUserId, listedUserIds)
            );
            for (Follow relation : myRelations) {
                followedUserIds.add(relation.getFollowUserId());
            }
        }

        //7.按原来的关系顺序组装结果
        List<FollowUserDTO> records = new ArrayList<>();

        for (Follow relation : relations) {
            Long listedUserId = followingList
                    ? relation.getFollowUserId()
                    : relation.getUserId();

            UserDTO user = usersById.get(listedUserId);
            String nickName =
                    user != null && StringUtils.hasText(user.nickName())
                            ? user.nickName()
                            : "未知用户";

            records.add(
                    new FollowUserDTO(
                            listedUserId.toString(),
                            nickName,
                            user == null ? null : user.icon(),
                            relation.getCreateTime(),
                            followedUserIds.contains(listedUserId)
                    )
            );
        }

        return new FollowPageDTO(
                records,
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    private Boolean readCachedState(
            Long userId,
            Long targetUserId
    ){
        String targetMember = targetUserId.toString();
        Map<Object, Boolean> membership = stringRedisTemplate.opsForSet().isMember(
                FollowRedisKeys.following(userId),
                FollowRedisKeys.INITIALIZED_MEMBER,
                targetMember
        );
        if (membership==null || !Boolean.TRUE.equals(membership.get(FollowRedisKeys.INITIALIZED_MEMBER))){
            return null;
        }
        return membership.get(targetMember);
    }
    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();

        if (userId == null || userId <= 0) {
            throw new IllegalStateException("当前用户上下文缺失");
        }

        return userId;
    }
    private void validateTargetUserId(Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException("目标用户 ID 必须大于 0");
        }
    }
}
