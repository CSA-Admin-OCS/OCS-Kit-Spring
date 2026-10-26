package com.open.spring.mvc.backups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.open.spring.mvc.bank.BankJpaRepository;
import com.open.spring.mvc.bathroom.TinkleJPARepository;
import com.open.spring.mvc.groups.Groups;
import com.open.spring.mvc.groups.GroupsJpaRepository;
import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonJpaRepository;
import com.open.spring.mvc.slack.CalendarEventService;

/** The shutdown endpoint backups: groups with members back up, and one failure does not stop the rest. */
class ShutdownBackupTest {

    @TempDir
    Path backups;

    BackupsController controller;
    GroupsJpaRepository groups;
    TinkleJPARepository tinkle;

    @BeforeEach
    void setUp() {
        controller = new BackupsController();
        groups = mock(GroupsJpaRepository.class);
        tinkle = mock(TinkleJPARepository.class);
        PersonJpaRepository people = mock(PersonJpaRepository.class);
        CalendarEventService calendar = mock(CalendarEventService.class);
        BankJpaRepository bank = mock(BankJpaRepository.class);
        when(people.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(calendar.getAllEvents()).thenReturn(List.of());
        when(bank.findAll()).thenReturn(List.of());
        when(tinkle.findAll()).thenReturn(List.of());

        Person member = new Person();
        member.setUid("student1");
        member.setName("Student One");
        when(groups.findAllWithMembers()).thenReturn(List.of(new Groups("Team A", "1", "CS", List.of(member))));

        ReflectionTestUtils.setField(controller, "groupsRepository", groups);
        ReflectionTestUtils.setField(controller, "tinkleRepository", tinkle);
        ReflectionTestUtils.setField(controller, "personRepository", people);
        ReflectionTestUtils.setField(controller, "calendarEventService", calendar);
        ReflectionTestUtils.setField(controller, "bankRepository", bank);
        ReflectionTestUtils.setField(controller, "backupBasePath", backups.toString());
        ReflectionTestUtils.setField(controller, "maxBackupFiles", 3);
    }

    @Test
    void groupsBackupLoadsMembersWithFetchJoin() throws IOException {
        List<String> failed = controller.performSpecificEndpointBackups();

        assertThat(failed).isEmpty();
        verify(groups, never()).findAll(); // lazy members fail outside a session at shutdown
        String json = Files.readString(single(backups.resolve("groups")));
        assertThat(json).contains("\"Team A\"").contains("\"student1\"");
    }

    @Test
    void oneFailingEndpointDoesNotStopTheOthers() throws IOException {
        when(tinkle.findAll()).thenThrow(new IllegalStateException("table missing"));

        List<String> failed = controller.performSpecificEndpointBackups();

        assertThat(failed).containsExactly("/api/tinkle/bulk/extract");
        for (String dir : List.of("person", "groups", "calendar", "bank")) {
            assertThat(single(backups.resolve(dir))).exists();
        }
    }

    private static Path single(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> all = files.toList();
            assertThat(all).hasSize(1);
            return all.get(0);
        }
    }
}
