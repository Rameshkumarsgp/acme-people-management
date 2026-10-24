package com.acme.peoplemanagement.directory.api;

import com.acme.peoplemanagement.directory.DirectoryService;
import com.acme.peoplemanagement.directory.rules.DirectoryParams;
import com.acme.peoplemanagement.directory.rules.DirectoryQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists employees for the directory. Errors are turned into problem+json by the shared exception handler. */
@RestController
@RequestMapping("/api/employees")
class DirectoryController {

    private final DirectoryService directoryService;

    DirectoryController(final DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping
    PagedResponse<DirectoryRow> list(final DirectoryParams params) {
        return directoryService.list(DirectoryQuery.from(params));
    }
}
