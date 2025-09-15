package com.example.share_note.dto.workspacemember;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMemberListResponseDto {
    private List<WorkspaceMemberResponseDto> members;
    private int totalCount;
}
