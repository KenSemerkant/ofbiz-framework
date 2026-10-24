package workeffort.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * P6-T2 guard tests: the forward-only status machine and the date guards
 * are the reason the aggregate exists — an effort that skips states or
 * ends before it starts is wrong by definition.
 */
class WorkEffortGuardTest {

    private static final LocalDate START = LocalDate.parse("2026-01-06");
    private static final LocalDate END = LocalDate.parse("2026-01-31");

    private static WorkEffort effort() {
        return new WorkEffort("WE-1", WorkEffortType.TASK, "wire the feed", null, START, END);
    }

    @Test
    void statusMovesForwardOnly() {
        WorkEffort effort = effort();
        assertEquals(WorkEffortStatus.CREATED, effort.statusId());
        effort.changeStatus(WorkEffortStatus.IN_PROGRESS);
        effort.changeStatus(WorkEffortStatus.COMPLETED);
        assertEquals(WorkEffortStatus.COMPLETED, effort.statusId());
        assertThrows(IllegalStatusTransitionException.class,
            () -> effort.changeStatus(WorkEffortStatus.IN_PROGRESS));
    }

    @Test
    void skipsAndBackwardsMovesAreRefused() {
        WorkEffort effort = effort();
        assertThrows(IllegalStatusTransitionException.class,
            () -> effort.changeStatus(WorkEffortStatus.COMPLETED), "CREATED skips IN_PROGRESS");
        assertThrows(IllegalStatusTransitionException.class,
            () -> effort.changeStatus(WorkEffortStatus.CREATED));
        assertThrows(IllegalStatusTransitionException.class,
            () -> effort.changeStatus(null));
        assertEquals(WorkEffortStatus.CREATED, effort.statusId());
    }

    @Test
    void cancelWorksFromAnyLiveStateButNeverFromTerminal() {
        WorkEffort created = effort();
        created.changeStatus(WorkEffortStatus.CANCELLED);
        assertEquals(WorkEffortStatus.CANCELLED, created.statusId());
        assertThrows(IllegalStatusTransitionException.class,
            () -> created.changeStatus(WorkEffortStatus.IN_PROGRESS));

        WorkEffort inProgress = WorkEffort.rehydrate("WE-2", WorkEffortType.PROJECT, "p",
            null, WorkEffortStatus.IN_PROGRESS, START, END);
        inProgress.changeStatus(WorkEffortStatus.CANCELLED);
        assertThrows(IllegalStatusTransitionException.class,
            () -> inProgress.changeStatus(WorkEffortStatus.COMPLETED));

        WorkEffort completed = WorkEffort.rehydrate("WE-3", WorkEffortType.EVENT, "e",
            null, WorkEffortStatus.COMPLETED, START, END);
        assertThrows(IllegalStatusTransitionException.class,
            () -> completed.changeStatus(WorkEffortStatus.CANCELLED));
    }

    @Test
    void constructionGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkEffort(" ", WorkEffortType.TASK, "n", null, START, END));
        assertThrows(IllegalArgumentException.class,
            () -> new WorkEffort("WE", null, "n", null, START, END));
        assertThrows(IllegalArgumentException.class,
            () -> new WorkEffort("WE", WorkEffortType.TASK, " ", null, START, END));
        assertThrows(IllegalArgumentException.class,
            () -> new WorkEffort("WE", WorkEffortType.TASK, "n", "WE", START, END),
            "self-parent");
        assertThrows(IllegalArgumentException.class,
            () -> new WorkEffort("WE", WorkEffortType.TASK, "n", null, END, START),
            "completion before start");
    }

    @Test
    void partialDatesAreAllowedButNotCalendarReady() {
        WorkEffort noDates = new WorkEffort("WE-4", WorkEffortType.TASK, "t", null, null, null);
        assertFalse(noDates.isCalendarReady());
        WorkEffort startOnly = new WorkEffort("WE-5", WorkEffortType.TASK, "t", null, START,
            null);
        assertFalse(startOnly.isCalendarReady());
        assertTrue(effort().isCalendarReady());
    }

    @Test
    void updateRevalidatesAndApplied() {
        WorkEffort effort = effort();
        effort.updateDetails("renamed", "WE-0", START, END);
        assertEquals("renamed", effort.workEffortName());
        assertEquals("WE-0", effort.parentWorkEffortId());
        assertThrows(IllegalArgumentException.class,
            () -> effort.updateDetails(" ", null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> effort.updateDetails("n", "WE-1", null, null), "self-parent");
        assertThrows(IllegalArgumentException.class,
            () -> effort.updateDetails("n", null, END, START));
        assertEquals("renamed", effort.workEffortName());
    }

    @Test
    void accessorsAndRehydrate() {
        WorkEffort effort = effort();
        assertEquals("WE-1", effort.workEffortId());
        assertEquals(WorkEffortType.TASK, effort.workEffortTypeId());
        assertEquals("wire the feed", effort.workEffortName());
        assertEquals(START, effort.estimatedStartDate());
        assertEquals(END, effort.estimatedCompletionDate());
        WorkEffort rehydrated = WorkEffort.rehydrate("WE-R", WorkEffortType.PROJECT, "p",
            "WE-1", WorkEffortStatus.IN_PROGRESS, START, END);
        assertEquals(WorkEffortStatus.IN_PROGRESS, rehydrated.statusId());
        assertEquals("WE-1", rehydrated.parentWorkEffortId());
    }

    @Test
    void statusMachinePredicates() {
        assertTrue(WorkEffortStatus.CREATED.isLive());
        assertTrue(WorkEffortStatus.IN_PROGRESS.isLive());
        assertFalse(WorkEffortStatus.COMPLETED.isLive());
        assertFalse(WorkEffortStatus.CANCELLED.isLive());
        assertTrue(WorkEffortStatus.CREATED.canTransitionTo(WorkEffortStatus.IN_PROGRESS));
        assertTrue(WorkEffortStatus.IN_PROGRESS.canTransitionTo(WorkEffortStatus.COMPLETED));
        assertFalse(WorkEffortStatus.COMPLETED.canTransitionTo(WorkEffortStatus.CANCELLED));
    }
}
