package com.izacare;

import com.izacare.domain.*;
import com.izacare.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.List;

/** 시연용 초기 데이터 — 데모 가게 "이자카야 데모점"(코드 DEMO01)에 소속 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(StoreRepository storeRepository,
                           MemberRepository memberRepository,
                           FoodItemRepository itemRepository,
                           DiningTableRepository tableRepository,
                           CourseRepository courseRepository,
                           NoticeRepository noticeRepository) {
        return args -> {
            if (storeRepository.count() > 0) return;

            Store store = storeRepository.save(new Store("이자카야 데모점", "DEMO01"));
            Long sid = store.getId();

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            // 데모 로그인: 가게코드 DEMO01 + (boss/1234 사장님, staff/1234 알바, pend/1234 승인대기)
            memberRepository.save(new Member(store, "boss", encoder.encode("1234"), "사장님",
                    Member.Role.OWNER, Member.Status.ACTIVE));
            memberRepository.save(new Member(store, "staff", encoder.encode("1234"), "김알바",
                    Member.Role.STAFF, Member.Status.ACTIVE));
            memberRepository.save(new Member(store, "pend", encoder.encode("1234"), "박신입",
                    Member.Role.STAFF, Member.Status.PENDING));

            itemRepository.saveAll(List.of(
                    new FoodItem(sid, "계란", "식자재", "개", 0, 3),
                    new FoodItem(sid, "오이", "채소", "개", 1, 3),
                    new FoodItem(sid, "소주", "주류", "병", 8, 5),
                    new FoodItem(sid, "맥주", "주류", "병", 20, 5),
                    new FoodItem(sid, "생맥주", "주류", "통", 10, 3),
                    new FoodItem(sid, "사케", "주류", "병", 15, 5),
                    new FoodItem(sid, "닭꼬치용 닭고기", "육류", "kg", 12, 4),
                    new FoodItem(sid, "타레 소스", "소스", "병", 6, 2)
            ));

            tableRepository.saveAll(List.of(
                    new DiningTable(sid, 1, 2),
                    new DiningTable(sid, 2, 2),
                    new DiningTable(sid, 3, 4),
                    new DiningTable(sid, 4, 2),
                    new DiningTable(sid, 5, 2),
                    new DiningTable(sid, 6, 6)
            ));

            courseRepository.saveAll(List.of(
                    new Course(sid, "음료/주류 무제한", null, true),
                    new Course(sid, "모둠사시미 코스 (120분)", 120, true),
                    new Course(sid, "사시미 코스 (120분)", 120, false),
                    new Course(sid, "일반 코스 요리", null, false),
                    new Course(sid, "모둠사시미 코스 (6종류)", null, false),
                    new Course(sid, "사시미 코스 (5종류)", null, false),
                    new Course(sid, "닭 요리 코스 (4종류)", null, false)
            ));

            noticeRepository.saveAll(List.of(
                    new Notice(sid, "회식", LocalDate.now().plusDays(1)),
                    new Notice(sid, "단체예약", LocalDate.now().plusDays(10))
            ));
        };
    }
}
