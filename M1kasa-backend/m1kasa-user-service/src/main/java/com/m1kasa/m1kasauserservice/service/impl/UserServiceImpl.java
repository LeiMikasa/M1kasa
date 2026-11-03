package com.m1kasa.m1kasauserservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.storage.MinioTemplate;
import com.m1kasa.m1kasauserservice.entity.User;
import com.m1kasa.m1kasauserservice.mapper.UserMapper;
import com.m1kasa.m1kasauserservice.service.UserService;
import com.m1kasa.user.api.UserDTO;
import com.m1kasa.user.api.UserProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {
    // 与后续头像上传接口生成的 UUID 文件名保持一致
    private static final Pattern AVATAR_FILE_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"
    );

    private final UserMapper userMapper;
    private final MinioTemplate minioTemplate;

    public UserServiceImpl(UserMapper userMapper, MinioTemplate minioTemplate) {
        this.userMapper = userMapper;
        this.minioTemplate = minioTemplate;
    }

    @Override
    public UserDTO getById(Long id) {
        User user = userMapper.selectById(id);

        if (user == null) {
            return null;
        }

        return toUserDTO(user);
    }

    @Override
    public List<UserDTO> listByIds(List<Long> ids) {
        //没有需要查询的用户，直接返回空列表
        if (ids==null || ids.isEmpty()){
            return List.of();
        }
        //限制一次查询的数量
        if (ids.size()>100){
            throw new IllegalArgumentException("一次最多查询 100 个用户");
        }
        //所有用户ID必须是正数
        boolean invalid = ids.stream().anyMatch(id -> id == null || id <= 0);

        if (invalid){
            throw new IllegalArgumentException("用户ID必须大于0");
        }
        //多篇笔记可能属于同一个作者，先去重
        List<Long> distinctIds = ids.stream().distinct().toList();

        //一次查询，只取对外展示需要的字段
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(
                                User::getId,
                                User::getNickName,
                                User::getIcon
                        )
                        .in(User::getId, distinctIds)
        );

        //将数据库实体转换成对外返回的DTO
        return users.stream()
                .map(this::toUserDTO)
                .toList();
    }

    @Override
    public boolean updateProfile(UserProfileUpdateRequest request) {
        //1.修改谁，由当前登录身份决定
        Long userId= UserContext.getUserId();

        if (userId==null||userId<=0){
            throw new IllegalStateException("当前登录用户上下文缺失");
        }
        if (request==null){
            throw new IllegalStateException("修改资料不能为空");
        }
        //2.校验昵称
        if (request.nickName()==null){
            throw new IllegalArgumentException("请输入昵称");
        }
        String nickName= request.nickName().strip();
        //按Unicode码点计数，避免常见标签被算成两个字符
        int length=nickName.codePointCount(0,nickName.length());
        if (length<1||length>20){
            throw new IllegalArgumentException("昵称长度必须为1~20");
        }
        //3.校验头像
        String avatarObjectKey=validateAvatar(userId, request.avatarObjectKey());
        //4.只更新允许修改的字段
        LambdaUpdateWrapper<User> update=new LambdaUpdateWrapper<User>()
                .eq(User::getId,userId)
                .set(User::getNickName,nickName)
                .set(User::getUpdateTime, LocalDateTime.now());
        // null 表示保留原头像，因此不把 icon 加进 UPDATE
        // 空字符串表示恢复默认头像，需要明确更新 icon
        if (avatarObjectKey != null) {
            update.set(User::getIcon, avatarObjectKey);
        }
        int rows = userMapper.update(null, update);
        // 相同资料重复保存时，驱动可能返回 0，
        // 此时再确认用户是否存在，避免误判成保存失败
        return rows > 0 || userMapper.selectById(userId) != null;
    }
    private String validateAvatar(Long userId, String objectKey) {
        // 没提交新头像：保留原头像
        if (objectKey == null) {
            return null;
        }

        String key = objectKey.strip();

        // 提交空字符串：恢复默认头像
        if (key.isEmpty()) {
            return "";
        }

        // 只能使用当前用户头像目录里的文件
        String prefix = "avatar/" + userId + "/";

        if (!key.startsWith(prefix)) {
            throw new IllegalArgumentException("只能使用本人上传的头像");
        }

        String fileName = key.substring(prefix.length());

        // 文件名必须符合上传接口生成的格式
        if (!AVATAR_FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("头像路径不合法，请重新上传");
        }

        // 路径格式正确，还需要确认文件实际存在
        if (!minioTemplate.exists(key)) {
            throw new IllegalArgumentException("头像不存在，请重新上传");
        }

        return key;
    }
    //头像访问链接有效期：10分钟
    private static final int AVATAR_URL_EXPIRY_SECONDS = 600;

    private UserDTO toUserDTO(User user) {
        String icon = user.getIcon();
        String iconUrl = null;

        if (StringUtils.hasText(icon)) {
            icon = icon.strip();

            if (icon.startsWith("avatar/")) {
                // 新头像：数据库保存的是 MinIO 文件路径
                iconUrl = minioTemplate.getPresignedGetUrl(
                        icon,
                        AVATAR_URL_EXPIRY_SECONDS
                );
            } else {
                // 兼容已有的图片 URL 或站内图片路径
                iconUrl = icon;
            }
        }

        return new UserDTO(
                user.getId(),
                user.getNickName(),
                iconUrl
        );
    }
}
