package com.cronquery.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a cron job with its schedule and execution details.
 */
@Schema(description = "A cron job with schedule and execution details")
public class CronJob {

    @Schema(description = "Cron expression (e.g., '0 8 * * 1-5')", 
            example = "0 8 * * 1-5")
    private String schedule;

    @Schema(description = "Command to be executed", 
            example = "/usr/bin/backup.sh")
    private String command;

    @Schema(description = "Source file or location of the cron job", 
            example = "/etc/crontab")
    private String source;

    @Schema(description = "User who owns the cron job", 
            example = "root")
    private String user;

    @Schema(description = "List of next scheduled execution times")
    private List<String> nextRuns;

    @Schema(description = "Human-readable description of the schedule", 
            example = "At 08:00 on weekdays")
    private String description;

    // Constructors
    public CronJob() {
        this.nextRuns = new ArrayList<>();
    }

    public CronJob(String schedule, String command, String source, String user, 
                   List<String> nextRuns, String description) {
        this.schedule = schedule;
        this.command = command;
        this.source = source;
        this.user = user;
        this.nextRuns = nextRuns != null ? nextRuns : new ArrayList<>();
        this.description = description;
    }

    // Getters and Setters
    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public List<String> getNextRuns() {
        return nextRuns;
    }

    public void setNextRuns(List<String> nextRuns) {
        this.nextRuns = nextRuns != null ? nextRuns : new ArrayList<>();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "CronJob{" +
                "schedule='" + schedule + '\'' +
                ", command='" + command + '\'' +
                ", source='" + source + '\'' +
                ", user='" + user + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
