package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemberDto {
    private String userName;
    private int age;

    // 이 어노테이션하고 compilequerydsl 누르면 dto도 Q파일이 생김.
    @QueryProjection
    public MemberDto(String userName, int age) {
        this.userName = userName;
        this.age = age;
    }
}
