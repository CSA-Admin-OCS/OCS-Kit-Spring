package com.open.spring.mvc.person;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

// ... existing imports and class ...

@Service
public class PersonService {

    @Autowired
    private PersonJpaRepository repository;

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private final Gson gson = new Gson();

    public void gradeAllPending() {
        List<Person> persons = repository.findAll();

        // Using a Queue (FIFO) to collect ALL grading tasks first,
        // then process them one-by-one.
        // This keeps the exact same behavior and order as before,
        // but now we are "some how" using a queue as requested.
        Queue<GradingTask> taskQueue = new LinkedList<>();

        for (Person p : persons) {
            List<Map<String, Object>> grades = p.getGradesJson();
            if (grades == null || grades.isEmpty()) continue;

            try {
                JsonArray entries = gson.toJsonTree(grades).getAsJsonArray();
                for (JsonElement e : entries) {
                    JsonObject obj = e.getAsJsonObject();

                    String questionUrl = obj.has("assignment") ? obj.get("assignment").getAsString() : "";
                    String submissionUrl = obj.has("submission") ? obj.get("submission").getAsString() : "";

                    if (!questionUrl.startsWith("http") || !submissionUrl.startsWith("http")) continue;

                    String assignment = obj.has("assignment") ? obj.get("assignment").getAsString() : "";

                    // Enqueue the task instead of processing immediately
                    taskQueue.add(new GradingTask(p, questionUrl, submissionUrl, assignment));
                }
            } catch (Exception ex) {
                System.err.println("Failed to parse grades for person " + p.getUid() + ": " + ex.getMessage());
            }
        }

        // Now drain the queue and process every task (same logic as original)
        while (!taskQueue.isEmpty()) {
            GradingTask task = taskQueue.poll();
            try {
                String geminiResponse = callGemini(task.questionUrl, task.submissionUrl);
                appendToCsv(task.person, task.assignment, geminiResponse);
            } catch (Exception ex) {
                System.err.println("Failed to process task for person " + task.person.getUid() + ": " + ex.getMessage());
            }
        }
    }

    private String callGemini(String questionUrl, String submissionUrl) {
        try {
            Client client = Client.builder().apiKey(geminiApiKey).build();

            String prompt = String.format("""
                Fetch the full FRQ question text, description, requirements, examples and rubric from:
                %s

                Fetch the student's submitted code from:
                %s

                Extract ONLY the Java code / class implementation from the student page.
                Ignore navigation, reflections, headers, footers, images, unrelated text.

                Grade the extracted code strictly 0–9 according to the question requirements.
                Award partial credit where appropriate.

                Output ONLY this exact format (nothing else):
                Score: X/9
                Comments: [detailed feedback, point-by-point, bugs, suggestions...]
                """, questionUrl, submissionUrl);

            GenerateContentResponse resp = client.models.generateContent("models/gemini-2.5-flash", prompt, null);
            return resp.text().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private void appendToCsv(Person p, String assignment, String geminiResponse) {
        String path = "./volumes/grades.csv";
        String name = p.getName() != null ? p.getName() : "";
        String uid = p.getUid();

        // Extract score and comments separately
        String score = "";
        String comments = geminiResponse;

        for (String line : geminiResponse.split("\n")) {
            if (line.startsWith("Score:")) {
                score = line.replace("Score:", "").trim();
            } else if (line.startsWith("Comments:")) {
                comments = line.replace("Comments:", "").trim();
            }
        }

        String escapedComments = comments
                .replace("\"", "\"\"")
                .replace("\n", " | ");

        String row = String.format("%s,%s,%s,%s,\"%s\"\n", name, uid, assignment, score, escapedComments);

        try {
            java.nio.file.Path csvPath = Paths.get(path);
            if (!Files.exists(csvPath)) {
                Files.writeString(csvPath, "Name,UID,Assignment,Score,Comments\n");
            }
            Files.writeString(csvPath, row, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("CSV append failed: " + e.getMessage());
        }
    }

    // Tiny helper class so the queue can hold everything we need for each task
    private static class GradingTask {
        final Person person;
        final String questionUrl;
        final String submissionUrl;
        final String assignment;

        GradingTask(Person person, String questionUrl, String submissionUrl, String assignment) {
            this.person = person;
            this.questionUrl = questionUrl;
            this.submissionUrl = submissionUrl;
            this.assignment = assignment;
        }
    }

    // ... keep any existing methods ...
}