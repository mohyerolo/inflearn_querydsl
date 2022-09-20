package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() throws Exception {
        // member1을 찾아라
        String qlString = "select m from Member m where m.userName = :userName";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("userName", "member1")
                .getSingleResult();

        assertThat(findMember.getUserName()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() throws Exception {
//        QMember m = new QMember("m");
//        QMember m = QMember.member;

        // 같은 테이블을 조인해야될때는 이름이 같으면 안 되니까 그런 경우에만 이런식으로
//        QMember m1 = new QMember("m1");

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.userName.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUserName()).isEqualTo("member1");
    }

    @Test
    public void search() throws Exception {
        Member findMember = queryFactory.selectFrom(member)
                .where(member.userName.eq("member1").and(member.age.eq(10)))
                .fetchOne();
        assertThat(findMember.getUserName()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() throws Exception {
        Member findMember = queryFactory.selectFrom(member)
                .where( // 여러개를 넘기면 다 and로 처리됨
                        member.userName.eq("member1"), member.age.eq(10)
                )
                .fetchOne();
        assertThat(findMember.getUserName()).isEqualTo("member1");
    }

    @Test
    public void resultFetchTest() throws Exception {
        List<Member> fetch = queryFactory.selectFrom(member)
                .fetch();
        Member fetchOne = queryFactory.selectFrom(member)
                .fetchOne();
        Member fetchFirst = queryFactory.selectFrom(member)
                .fetchFirst();
        QueryResults<Member> results = queryFactory.selectFrom(member)
                .fetchResults();
        List<Member> content = results.getResults();

        long total = queryFactory.selectFrom(member)
                .fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순
     * 2. 회원 이름 올림차순
     * 단, 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() throws Exception {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.userName.asc().nullsLast())
                .fetch();
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUserName()).isEqualTo("member5");
        assertThat(member6.getUserName()).isEqualTo("member6");
        assertThat(memberNull.getUserName()).isNull();

    }

    @Test
    public void paging1() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.userName.desc())
                .offset(1)
                .limit(2)
                .fetch();
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() throws Exception {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.userName.desc())
                .offset(1)
                .limit(2)
                .fetchResults();
        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() throws Exception {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        // 튜플을 쓰는 이유: 데이터 타입이 여러 개 들어와서.
        // 실무에서는 DTO를 사용
        Tuple tuple = result.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라
     */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();
        assertThat(result)
                .extracting("userName")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인
     * 회원의 이름이 팀 이름과 같은 회원을 조회
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        // 모든 회원과 팀을 가져와서 조인하고 where절로 필터링 함 -> 세타조인
        List<Member> result = queryFactory
                .select(member)
                .from(member, team) // 세타 조인은 프럼 절에 그냥 나열하는 것
                .where(member.userName.eq(team.name))
                .fetch();

        assertThat(result).extracting("userName")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
//                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));


        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                /**
                 *  leftJoin(member.team, team)이 아님.
                 *  id로 매칭하는 게 아니라 이름으로 조인이 됨.
                 */
                .leftJoin(team).on(member.userName.eq(team.name))
//                .join(team).on(member.userName.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() throws Exception {
        em.flush();
        em.clear();

        // LAZY이기때문에 db에서 조회할때 member만 조회가 되고 팀은 조회가 안 됨
        Member findMember = queryFactory.selectFrom(member)
                .where(member.userName.eq("member1"))
                .fetchOne();

        // findMember.getTeam()이 이미 로딩된 entity인지, 초기화가 안 된건지 알려줌
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();

        // member를 조회할 때 연관된 팀을 가지고옴
        /**
         * select
         *    member1
         * from
         *    Member member1
         * inner join
         *    fetch member1.team as team
         */
        Member findMember = queryFactory.selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.userName.eq("member1"))
                .fetchOne();

        // findMember.getTeam()이 이미 로딩된 entity인지, 초기화가 안 된건지 알려줌
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     * @throws Exception
     */
    @Test
    public void subQuery() throws Exception {
        QMember memberSub = new QMember("memberSub");
        /**
         * select member1
         * from Member member1
         * where member1.age = (select max(memberSub.age)
         * from Member memberSub)
         */
        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     * @throws Exception
     */
    @Test
    public void subQueryGoe() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     * @throws Exception
     */
    @Test
    public void subQueryIn() throws Exception {
        QMember memberSub = new QMember("memberSub");
        /**
         * select
         *         member1
         *     from
         *         Member member1
         *     where
         *         member1.age in (
         *             select
         *                 memberSub.age
         *             from
         *                 Member memberSub
         *             where
         *                 memberSub.age > ?1
         *         )
         */
        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     * @throws Exception
     */
    @Test
    public void selectSubQuery() throws Exception {
        QMember memberSub = new QMember("memberSub");

        /**
         * select
         *         member1.userName,
         *         (select
         *             avg(memberSub.age)
         *         from
         *             Member memberSub)
         *     from
         *         Member member1
         */
        List<Tuple> result = queryFactory
                .select(member.userName,
                        select(memberSub.age.avg())
                                .from(memberSub)
                )
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void basicCase() throws Exception {
        /**
         * select
         *         case
         *             when member1.age = ?1 then ?2
         *             when member1.age = ?3 then ?4
         *             else '기타'
         *         end
         *     from
         *         Member member1
         */
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase() throws Exception {
        List<String> result = queryFactory.
                select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void caseOrderBy() throws Exception {
        NumberExpression<Integer> rankPath = new CaseBuilder()
                .when(member.age.between(0, 20)).then(2)
                .when(member.age.between(21, 30)).then(1)
                .otherwise(3);

        /**
         *    select
         *         member1.userName,
         *         member1.age,
         *         case
         *             when (member1.age between ?1 and ?2) then ?3
         *             when (member1.age between ?4 and ?5) then ?6
         *             else 3
         *         end
         *     from
         *         Member member1
         *     order by
         *         case
         *             when (member1.age between ?7 and ?8) then ?9
         *             when (member1.age between ?10 and ?11) then ?12
         *             else 3
         *         end desc
         */
        List<Tuple> result = queryFactory
                .select(member.userName, member.age, rankPath)
                .from(member)
                .orderBy(rankPath.desc())
                .fetch();

        for (Tuple tuple : result) {
            String userName = tuple.get(member.userName);
            Integer age = tuple.get(member.age);
            Integer rank = tuple.get(rankPath);
            System.out.println("userName = " + userName + " age = " + age + " rank = "
                    + rank);
        }
    }

    @Test
    public void constant() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.userName, Expressions.constant("A"))
                .from(member)
                .fetch();

        /**
         * tuple = [member1, A]
         * tuple = [member2, A]
         * tuple = [member3, A]
         * tuple = [member4, A]
         */
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() throws Exception {
        // username_age
        /**
         * select
         *         concat(concat(member1.userName,
         *         ?1),
         *         str(member1.age))
         *     from
         *         Member member1
         *     where
         *         member1.userName = ?2
         */
        List<String> result = queryFactory
                .select(member.userName.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.userName.eq("member1"))
                .fetch();

        /**
         * s = member1_10
         */
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }







    /**
     * 프로젝션과 결과 반환
     */

    @Test
    public void simpleProjection() throws Exception {
        // List<Member>여도 됨. 그래도 객체 하나 반환인거니까.
        List<String> result = queryFactory
                .select(member.userName)
                .from(member)
                .fetch();

        /**
         * s = member1
         * s = member2
         * s = member3
         * s = member4
         */
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection() throws Exception {
        // 여러 개가 select절로 넘어오니까 tuple이 쓰이는 것
        /**
         * tuple은 com.querydsl.core 패키지임.
         * repository 계층 안에서는 괜찮은데 그걸 넘어 서비스나 컨트롤러까지
         * 넘어가는 건 좋지 않음. JPA나 querydsl을 쓴다는 걸 핵심 비즈니스 로직이나
         * 서비스가 알면 좋지 않음. 마찬가지로 걔네가 반환하는 resultSet과 같은 거를 repository나 dao안에서만
         * 쓰도록 하고 나머지 계층에서는 의존이 없게 설계하는 게 좋은 설계.
         * 하부 기술을 다른 걸로 바꾸게돼도 앞단이 바뀔 문제가 없는 것.
         * 바깥 계층으로 보내는건 DTO로 바꿔서 보내기.
         *
         * -> tuple은 repository 안에서만 쓰고 tuple로 repository 안에서 정리하고
         * 바깥에 나갈때는 DTO로 변환해서 나가기.
         */
        List<Tuple> result = queryFactory
                .select(member.userName, member.age)
                .from(member)
                .fetch();

        /**
         * userName = member1
         * age = 10
         * userName = member2
         * age = 20
         * userName = member3
         * age = 30
         * userName = member4
         * age = 40
         */
        for (Tuple tuple : result) {
            String userName = tuple.get(member.userName);
            Integer age = tuple.get(member.age);
            System.out.println("userName = " + userName);
            System.out.println("age = " + age);
        }
    }

    @Test
    public void findDtoByJPQL() throws Exception {
        /**
         * select
         *             member0_.user_name as col_0_0_,
         *             member0_.age as col_1_0_
         *         from
         *             member member0_
         */
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.userName, m.age)" +
                        " from Member m", MemberDto.class)
                .getResultList();

        /**
         * memberDto = MemberDto(userName=member1, age=10)
         * memberDto = MemberDto(userName=member2, age=20)
         * memberDto = MemberDto(userName=member3, age=30)
         * memberDto = MemberDto(userName=member4, age=40)
         */
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoBySetter() throws Exception {
        /**
         * 여기서 MemberDto.class를 하면 기본 생성자가 있어야 한다.
         * 그래서 Dto에 NoArgsConstructor 있어야됨
         */
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.userName, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * field는 dto의 getter, setter 무시하고 바로 필드에 값을 넣는다
     */
    @Test
    public void findDtoByField() throws Exception {
        /**
         * 여기서 MemberDto.class를 하면 기본 생성자가 있어야 한다.
         * 그래서 Dto에 NoArgsConstructor 있어야됨
         */
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.userName, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByConstructor() throws Exception {
        /**
         * dto에 있는 생성자와 인수가 맞아야 됨.
         */
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.userName, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findUserDtoByField() throws Exception {
        /**
         * Projections.fields(UserDto.class,
         *                         member.userName, member.age))
         * 이 코드로 돌리면 member.userName이 매칭되는 게 UserDto에 없어서
         * userDto = UserDto(name=null, age=10)
         * userDto = UserDto(name=null, age=20)
         * userDto = UserDto(name=null, age=30)
         * userDto = UserDto(name=null, age=40)
         * 이런 결과가 나옴
         * 이때는 .as를 붙이면 됨
         */
//        List<UserDto> result = queryFactory
//                .select(Projections.fields(UserDto.class,
//                        member.userName.as("name"), member.age))
//                .from(member)
//                .fetch();
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.userName.as("name"),
                        /**
                         * 서브쿼리를 쓰고 싶을 때는 ExpressionUtils.as로 한 번 감싸면
                         * alias를 사용할 수 있음
                         */
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max()).from(memberSub), "age")
                        )
                )
                .from(member)
                .fetch();
        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * constructor는 select절에
     * Projections.constructor(MemberDto.class,member.userName, member.age, member.id, member.~)
     * 위와 같이 현재 memberDto에 정의되지 않은 생성자 형태로 인수를 넣어도 컴파일에서 잡아내지 못 한다.
     * 런타임에 오류가 남.
     * 그러나 쿼리프로젝션으로 하면 컴파일 오류로 잡아낼 수 있음.
     * 이미 타입으로 두 가지만 받아내도록 되어있기 때문.
     * 생성자도 호출되는 걸 보장해줌 (memberDto 생성자에서 출력문 작성해보면 호출되는거 확인 가능)
     */
    @Test
    public void findDtoByQueryProjection() throws Exception {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.userName, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }





    /**
     * 동적 쿼리
     */

    // 파라미터의 값이 null인지 아닌지에 따라 바뀌어야 됨
    @Test
    public void dynamicQuery_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        // if) username 값이 필수
        // BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond);

        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.userName.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    public void dynamicQuery_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
//                .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond, ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.userName.eq(usernameCond) : null;

    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }





    /**
     * 벌크 연산
     */

    @Test
//    @Commit
    public void bulkUpdate() throws Exception {
        // member1= 10 -> 비회원, member2= 20 -> 비회원
        // member3 = 30 -> member3, member4= 40 -> member4

        /**
         * 문제: 영속성 컨텍스트에는 member1,2의 name이 여전히 예전 값임.
         * 벌크 연산은 영속성 컨텍스트를 무시하고 db에 쿼리가 바로 나감.
         * 그래서 db와 영속성 컨텍스트의 상태가 달라짐
         */
        long count = queryFactory
                .update(member)
                .set(member.userName, "비회원")
                .where(member.age.lt(28))
                .execute();


        /**
         * db의 값이 달라졌어도 영속성 컨텍스트에 그 값이 이미 있으면 JPA는 db에서
         * 새로 가져온걸로 바꾸지 않음. 영속성 컨텍스트가 항상 우선권을 가져서 그대로 유지됨.
         *
         * member1 = Member(id=3, userName=member1, age=10)
         * member1 = Member(id=4, userName=member2, age=20)
         * member1 = Member(id=5, userName=member3, age=30)
         * member1 = Member(id=6, userName=member4, age=40)
         */
//        List<Member> result = queryFactory
//                .selectFrom(member)
//                .fetch();
//
//        for (Member member1 : result) {
//            System.out.println("member1 = " + member1);
//        }

        // 그래서 bulk 연산이 실행되면 영속성 컨텍스트를 초기화하면 됨
        em.flush();
        em.clear();

        /**
         * member1 = Member(id=3, userName=비회원, age=10)
         * member1 = Member(id=4, userName=비회원, age=20)
         * member1 = Member(id=5, userName=member3, age=30)
         * member1 = Member(id=6, userName=member4, age=40)
         * 바뀐 걸 확인할 수 있음
         */
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    @Test
    public void bulkAdd() throws Exception {
        /**
         * update
         *             member
         *         set
         *             age=age+?
         */
        long execute = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    @Test
    public void bulkDelete() throws Exception {
        long execute = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }





    /**
     * SQL function 호출하기
     */
    @Test
    public void sqlFunction() throws Exception {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                        member.userName, "member", "M"))
                .from(member)
                .fetch();

        /**
         * select
         *             replace(member0_.user_name,
         *             ?,
         *             ?) as col_0_0_
         *         from
         *             member member0_
         *
         * s = M1
         * s = M2
         * s = M3
         * s = M4
         */
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() throws Exception {
        List<String> result = queryFactory
                .select(member.userName)
                .from(member)
//                .where(member.userName.eq(
//                        Expressions.stringTemplate("function('lower', {0})",
//                                member.userName)))
                .where(member.userName.eq(member.userName.lower()))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
