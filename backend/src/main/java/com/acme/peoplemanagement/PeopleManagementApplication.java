package com.acme.peoplemanagement;

import com.acme.peoplemanagement.dataload.BatchMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class PeopleManagementApplication {

    public static void main(final String[] args) {
        final ConfigurableApplicationContext context = SpringApplication.run(PeopleManagementApplication.class, args);
        if (BatchMode.requestedBy(context.getEnvironment())) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
