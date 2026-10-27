package com.acme.peoplemanagement.insights.api;

import com.acme.peoplemanagement.insights.InsightsService;
import com.acme.peoplemanagement.insights.rules.ExtremesParams;
import com.acme.peoplemanagement.insights.rules.ExtremesQuery;
import com.acme.peoplemanagement.insights.rules.InsightsParams;
import com.acme.peoplemanagement.insights.rules.InsightsQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Answers how ACME pays people. Errors are turned into problem+json by the shared exception handler. */
@RestController
@RequestMapping("/api/insights")
class InsightsController {

    private final InsightsService insightsService;

    InsightsController(final InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping
    InsightsResponse insights(final InsightsParams params) {
        return insightsService.insights(InsightsQuery.from(params));
    }

    @GetMapping("/extremes")
    ExtremesResponse extremes(final ExtremesParams params) {
        return insightsService.extremes(ExtremesQuery.from(params));
    }
}
