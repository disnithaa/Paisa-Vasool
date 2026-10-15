package com.paisavasool.app.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paisavasool.app.api.dto.AddMemberRequest;
import com.paisavasool.app.api.dto.CreateGroupRequest;
import com.paisavasool.app.api.dto.GroupResponse;
import com.paisavasool.app.api.dto.MemberResponse;
import com.paisavasool.app.domain.group.GroupService;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<GroupResponse> listGroups() {
        return groupService.listGroups();
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.createGroup(request.name());
        return ResponseEntity.created(URI.create("/api/groups/" + response.id())).body(response);
    }

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(@PathVariable UUID groupId) {
        return groupService.getGroup(groupId);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<MemberResponse> addMember(
        @PathVariable UUID groupId,
        @Valid @RequestBody AddMemberRequest request
    ) {
        MemberResponse response = groupService.addMember(groupId, request);
        return ResponseEntity.created(URI.create("/api/groups/" + groupId + "/members/" + response.id()))
            .body(response);
    }

    @GetMapping("/{groupId}/members")
    public List<MemberResponse> listMembers(@PathVariable UUID groupId) {
        return groupService.listMembers(groupId);
    }
}
