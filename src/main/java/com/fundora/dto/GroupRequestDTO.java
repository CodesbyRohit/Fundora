package com.fundora.dto;

import com.fundora.model.GroupType;
import java.util.List;

/**
 * Module VI: Request Payload DTO for Creating a New Group
 */
public class GroupRequestDTO {
    private String name;
    private String description;
    private GroupType groupType = GroupType.FLATMATE_RECURRING;
    private Long createdByUserId;
    private List<Long> memberIds;

    public GroupRequestDTO() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GroupType getGroupType() {
        return groupType;
    }

    public void setGroupType(GroupType groupType) {
        this.groupType = groupType;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public List<Long> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<Long> memberIds) {
        this.memberIds = memberIds;
    }
}
