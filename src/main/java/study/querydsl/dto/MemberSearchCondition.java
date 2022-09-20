package study.querydsl.dto;

import lombok.Data;

@Data
public class MemberSearchCondition {
    //회원명, 팀명, 나이(ageGoe, ageLoe)

    private String userName;
    private String teamName;
    private Integer ageGoe;     // 값이 null일수도
    private Integer ageLoe;

}
