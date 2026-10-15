package com.paisavasool.app.domain.group;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.AddMemberRequest;
import com.paisavasool.app.api.dto.GroupResponse;
import com.paisavasool.app.api.dto.MemberResponse;
import com.paisavasool.app.api.error.ConflictException;
import com.paisavasool.app.api.error.ForbiddenException;
import com.paisavasool.app.api.error.ResourceNotFoundException;
import com.paisavasool.app.domain.user.CurrentUserService;
import com.paisavasool.app.domain.user.User;
import com.paisavasool.app.domain.user.UserRepository;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public GroupService(
        GroupRepository groupRepository,
        GroupMembershipRepository membershipRepository,
        UserRepository userRepository,
        CurrentUserService currentUserService
    ) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public GroupResponse createGroup(String name) {
        User creator = currentUserService.requireCurrentUser();
        Group group = groupRepository.saveAndFlush(new Group(name));
        membershipRepository.saveAndFlush(new GroupMembership(
            creator, group, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID groupId) {
        Group group = findGroup(groupId);
        requireActiveMember(groupId);
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups() {
        User currentUser = currentUserService.requireCurrentUser();
        return membershipRepository.findAllByUser_IdAndStatusOrderByJoinedAtAsc(
                currentUser.getId(),
                MembershipStatus.ACTIVE
            )
            .stream()
            .map(membership -> toResponse(membership.getGroup()))
            .toList();
    }

    @Transactional
    public MemberResponse addMember(UUID groupId, AddMemberRequest request) {
        Group group = findGroup(groupId);
        requireActiveMember(groupId);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseGet(() -> userRepository.saveAndFlush(new User(request.name(), email)));

        if (membershipRepository.existsByGroup_IdAndUser_Id(groupId, user.getId())) {
            throw new ConflictException("User already has a membership record in this group");
        }

        GroupMembership membership = membershipRepository.saveAndFlush(new GroupMembership(
            user, group, MembershipRole.MEMBER, MembershipStatus.ACTIVE
        ));
        return toMemberResponse(membership);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID groupId) {
        findGroup(groupId);
        requireActiveMember(groupId);
        return membershipRepository.findAllByGroup_IdOrderByJoinedAtAsc(groupId).stream()
            .map(GroupService::toMemberResponse)
            .toList();
    }

    public void requireActiveMember(UUID groupId) {
        User currentUser = currentUserService.requireCurrentUser();
        if (!membershipRepository.existsByGroup_IdAndUser_IdAndStatus(
            groupId, currentUser.getId(), MembershipStatus.ACTIVE
        )) {
            throw new ForbiddenException("An active group membership is required");
        }
    }

    private Group findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }

    private static GroupResponse toResponse(Group group) {
        return new GroupResponse(group.getId(), group.getName(), group.getCreatedAt());
    }

    private static MemberResponse toMemberResponse(GroupMembership membership) {
        User user = membership.getUser();
        return new MemberResponse(
            membership.getId(),
            user.getId(),
            user.getName(),
            user.getEmail(),
            membership.getRole(),
            membership.getStatus(),
            membership.getJoinedAt()
        );
    }
}
