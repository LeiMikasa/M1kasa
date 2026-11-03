package com.m1kasa.m1kasauserservice.service;

import com.m1kasa.user.api.FollowPageDTO;
import com.m1kasa.user.api.FollowStateDTO;

public interface FollowService {

    /**
     * 设置关注状态：
     * true 表示关注，false 表示取消关注。
     */
    FollowStateDTO setFollowed(
            Long targetUserId,
            boolean followed
    );

    /**
     * 查询当前登录用户是否关注了目标作者。
     */
    FollowStateDTO getFollowState(
            Long targetUserId
    );

    /**
     * 分页查询当前用户关注的人
     * */
    FollowPageDTO findFollowing(int current,int size);

    /**
     * 分页查询当前用户的粉丝
     * */
    FollowPageDTO findFollowers(int current,int size);
}
