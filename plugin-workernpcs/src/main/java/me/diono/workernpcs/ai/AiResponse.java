package me.diono.workernpcs.ai;

import java.util.Map;

public class AiResponse {
    private final String message;
    private final String task;
    private final Map<String, Object> taskParams;

    public AiResponse(String message, String task, Map<String, Object> taskParams) {
        this.message = message;
        this.task = task;
        this.taskParams = taskParams;
    }

    public String getMessage() { return message; }
    public String getTask() { return task; }
    public Map<String, Object> getTaskParams() { return taskParams; }
}
