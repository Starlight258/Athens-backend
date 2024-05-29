package com.attica.athens.domain.agora.application;

import com.attica.athens.domain.agora.dao.AgoraRepository;
import com.attica.athens.domain.agora.dao.CategoryRepository;
import com.attica.athens.domain.agora.domain.Agora;
import com.attica.athens.domain.agora.domain.AgoraStatus;
import com.attica.athens.domain.agora.domain.Category;
import com.attica.athens.domain.agora.dto.SimpleAgoraResult;
import com.attica.athens.domain.agora.dto.request.AgoraCreateRequest;
import com.attica.athens.domain.agora.dto.request.AgoraParticipateRequest;
import com.attica.athens.domain.agora.dto.request.SearchCategoryRequest;
import com.attica.athens.domain.agora.dto.request.SearchKeywordRequest;
import com.attica.athens.domain.agora.dto.response.AgoraParticipateResponse;
import com.attica.athens.domain.agora.dto.response.AgoraSlice;
import com.attica.athens.domain.agora.dto.response.CreateAgoraResponse;
import com.attica.athens.domain.agora.exception.NotFoundAgoraException;
import com.attica.athens.domain.agora.dto.response.EndVoteAgoraResponse;
import com.attica.athens.domain.agora.dto.response.StartAgoraResponse;
import com.attica.athens.domain.agora.exception.InvalidAgoraStatusChangeException;
import com.attica.athens.domain.agora.exception.NotFoundCategoryException;
import com.attica.athens.domain.agoraUser.dao.AgoraUserRepository;
import com.attica.athens.domain.agoraUser.domain.AgoraUser;
import com.attica.athens.domain.user.dao.UserRepository;
import com.attica.athens.domain.user.domain.User;
import com.attica.athens.domain.user.exception.NotFoundUserException;
import com.attica.athens.domain.agoraUser.exception.AlreadyVotedException;
import com.attica.athens.domain.agoraUser.exception.NotFoundAgoraUserException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgoraService {

    private final AgoraRepository agoraRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AgoraUserRepository agoraUserRepository;

    public AgoraSlice<SimpleAgoraResult> findAgoraByKeyword(final String agoraName,
                                                            final SearchKeywordRequest request) {
        return agoraRepository.findAgoraByKeyword(request.next(), request.getStatus(), agoraName);
    }

    public AgoraSlice<SimpleAgoraResult> findAgoraByCategory(final SearchCategoryRequest request) {
        List<Long> categoryIds = findParentCategoryById(request.category());
        return agoraRepository.findAgoraByCategory(request.next(), request.getStatus(), categoryIds);
    }

    @Transactional
    public CreateAgoraResponse create(final AgoraCreateRequest request) {
        Category category = findByCategory(request.categoryId());

        Agora created = agoraRepository.save(createAgora(request, category));
        return new CreateAgoraResponse(created.getId());
    }

    @Transactional
    public AgoraParticipateResponse participate(final Long userId, final Long agoraId, final AgoraParticipateRequest request) {
        Agora agora = findAgoraById(agoraId);
        AgoraUser created = createAgoraUser(userId, agoraId, request);
        AgoraUser agoraUser = agoraUserRepository.save(created);
        agora.addUser(agoraUser);

        String userUuid = agoraUser.getUuid();

        return new AgoraParticipateResponse(created.getAgora().getId(), userUuid, created.getType());
    }

    private Agora createAgora(final AgoraCreateRequest request, final Category category) {
        return new Agora(request.title(),
                request.capacity(),
                request.duration(),
                request.color(),
                category);
    }

    private AgoraUser createAgoraUser(final Long userId, final Long agoraId, final AgoraParticipateRequest request) {
        return new AgoraUser(
                request.getAgoraUserType(),
                request.nickname(),
                request.photoNum(),
                findAgoraById(agoraId),
                findUserById(userId)
        );
    }

    private User findUserById(final Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundUserException(userId));
    }

    private List<Long> findParentCategoryById(final Long categoryId) {
        Category category = findByCategory(categoryId);
        List<Long> parentCodes = new ArrayList<>();
        Long currentCategory = category.getId();

        while (currentCategory != null) {
            parentCodes.add(currentCategory);
            currentCategory = categoryRepository.findById(currentCategory)
                    .map(Category::getParent)
                    .map(Category::getId)
                    .orElse(null);
        }

        return parentCodes;
    }

    private Category findByCategory(final Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundCategoryException(categoryId));
    }

    @Transactional
    public StartAgoraResponse startAgora(Long agoraId, Long userId) {
        Agora agora = findAgoraById(agoraId);

        boolean isExists = existsByAgoraIdAndUserId(agoraId, userId);
        if (!isExists) {
            throw new NotFoundAgoraUserException(agoraId, userId);
        }

        agora.startAgora();

        return new StartAgoraResponse(agora);
    }

    private boolean existsByAgoraIdAndUserId(Long agoraId, Long userId) {
        return agoraUserRepository.existsByAgoraIdAndUserId(agoraId, userId);
    }

    private Agora findAgoraById(Long agoraId) {
        return agoraRepository.findById(agoraId)
                .orElseThrow(() -> new NotFoundAgoraException(agoraId));
    }

    @Transactional
    public EndVoteAgoraResponse endVoteAgora(Long agoraId, Long userId) {
        Agora agora = findAgoraById(agoraId);
        if (!(agora.getStatus() == AgoraStatus.RUNNING || agora.getStatus() == AgoraStatus.CLOSED)) {
            throw new InvalidAgoraStatusChangeException(agoraId);
        }

        findAgoraUserAndMarkEndVoted(agoraId, userId);

        int participantCount = agoraUserRepository.countByAgoraId(agoraId);
        agora.incrementEndVoteCountAndCheckTermination(participantCount);

        return new EndVoteAgoraResponse(agora);
    }

    private void findAgoraUserAndMarkEndVoted(Long agoraId, Long userId) {
        AgoraUser agoraUser = findAgoraUserByAgoraIdAndUserId(agoraId, userId);
        if (agoraUser.isEndVoted()) {
            throw new AlreadyVotedException();
        }
        agoraUser.markEndVoted();
    }

    private AgoraUser findAgoraUserByAgoraIdAndUserId(Long agoraId, Long userId) {
        return agoraUserRepository.findByAgoraIdAndUserId(agoraId, userId)
                .orElseThrow(() -> new NotFoundAgoraUserException(agoraId, userId));
    }
}