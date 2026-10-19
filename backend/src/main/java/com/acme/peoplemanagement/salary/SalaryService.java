package com.acme.peoplemanagement.salary;

import com.acme.peoplemanagement.salary.rules.SalaryTimelineValidator;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way salary entries are written, so every change is checked against the rules first.
 * Callers pass today, so one request reads the clock once.
 */
@Service
public class SalaryService {

    private final SalaryEntryRepository salaryEntryRepository;
    private final SalaryTimelineValidator validator = new SalaryTimelineValidator();

    SalaryService(final SalaryEntryRepository salaryEntryRepository) {
        this.salaryEntryRepository = salaryEntryRepository;
    }

    /** Joins the caller's transaction, so a broken rule also undoes the employee row. */
    @Transactional
    public void recordFirstEntry(
            final long employeeId, final LocalDate hireDate, final TimelineEntry entry, final LocalDate today) {
        validator.validate(hireDate, List.of(entry), today);
        salaryEntryRepository.save(new SalaryEntry(employeeId, entry));
    }

    @Transactional(readOnly = true)
    public Optional<TimelineEntry> currentSalary(final long employeeId, final LocalDate today) {
        return salaryEntryRepository
                .findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employeeId, today)
                .map(SalaryEntry::toTimelineEntry);
    }
}
