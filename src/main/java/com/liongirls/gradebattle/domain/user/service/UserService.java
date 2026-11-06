package com.liongirls.gradebattle.domain.user.service;

import com.liongirls.gradebattle.global.exception.GeneralException;
import com.liongirls.gradebattle.domain.user.dto.SignupRequest;
import com.liongirls.gradebattle.domain.user.dto.UserResponse;
import com.liongirls.gradebattle.domain.user.entity.User;
import com.liongirls.gradebattle.domain.user.exception.UserErrorCode;
import com.liongirls.gradebattle.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new GeneralException(UserErrorCode.INVALID_PASSWORD);
        }
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new GeneralException(UserErrorCode.DUPLICATE_LOGIN_ID);
        }
        User user = new User(request.loginId(), passwordEncoder.encode(request.password()), request.nickname().strip());
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // 동시에 가입한 경우에도 DB의 UNIQUE 제약으로 아이디 중복을 차단한다.
            throw new GeneralException(UserErrorCode.DUPLICATE_LOGIN_ID);
        }
    }

    public UserResponse getMe(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateNickname(Long userId, String nickname) {
        User user = findUser(userId);
        user.changeNickname(nickname.strip());
        return UserResponse.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.NOT_FOUND));
    }
}
