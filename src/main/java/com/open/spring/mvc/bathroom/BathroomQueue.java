package com.open.spring.mvc.bathroom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class BathroomQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column
    private String teacherEmail;
    private String peopleQueue;
    private int away;

    @Column(columnDefinition = "int default 1")
    private int maxOccupancy = 1;

    // Custom constructor

    /**
     * Constructor which creates each element in the queue
     * 
     * @param teacherEmail - the teacher's email for what class they are from
     * @param peopleQueue  - the people in the queue
     */
    public BathroomQueue(String teacherEmail, String peopleQueue) {
        this.teacherEmail = teacherEmail;
        this.peopleQueue = peopleQueue;
        this.away = 0;
    }

    /**
     * Function to add a student to the queue
     * 
     * @param studentName - the name you want to add to the queue
     */
    public void addStudent(String studentName) {
        if (this.peopleQueue == null || this.peopleQueue.isEmpty()) {
            this.peopleQueue = studentName;
        } else {
            this.peopleQueue += "," + studentName;
        }
    }

    /**
     * Helper to check if a student is already in the queue
     */
    public boolean containsStudent(String studentName) {
        if (this.peopleQueue == null || this.peopleQueue.isEmpty())
            return false;
        return Arrays.asList(this.peopleQueue.split(",")).contains(studentName);
    }

    /**
     * Helper to get the index of a student in the queue
     */
    public int getStudentIndex(String studentName) {
        if (this.peopleQueue == null || this.peopleQueue.isEmpty())
            return -1;
        List<String> students = Arrays.asList(this.peopleQueue.split(","));
        return students.indexOf(studentName);
    }

    /**
     * Function to remove the student from a queue
     * 
     * @param studentName - the name you want to remove from the queue.
     */
    public void removeStudent(String studentName) {
        if (this.peopleQueue != null && !this.peopleQueue.isEmpty()) {
            String[] studentsBefore = this.peopleQueue.split(",");
            int studentIndex = -1;
            for (int i = 0; i < studentsBefore.length; i++) {
                if (studentsBefore[i].equals(studentName)) {
                    studentIndex = i;
                    break;
                }
            }

            if (studentIndex != -1) {
                // Remove the student
                this.peopleQueue = Arrays.stream(studentsBefore)
                        .filter(s -> !s.equals(studentName))
                        .collect(Collectors.joining(","));

                // ONLY decrease away if the student was actually in the "away" portion
                if (studentIndex < this.away) {
                    if (this.away > 0) {
                        this.away--;
                    }
                }
            }
        }
    }

    /**
     * @return - returns the student who is at the front of the line
     */
    public String getFrontStudent() {
        if (this.peopleQueue != null && !this.peopleQueue.isEmpty()) {
            return this.peopleQueue.split(",")[0];
        }
        return null;
    }

    /**
     * Students need to be approved to go to the bathroom by the teacher
     * When they are, their status is set to away
     * When they return, they are removed from the queue
     */
    public void approveStudent() {
        if (this.peopleQueue != null && !this.peopleQueue.isEmpty()) {
            if (this.away < this.maxOccupancy) {
                // Determine how many people are actually in the queue
                int totalInQueue = this.peopleQueue.split(",").length;
                // We can't have more people 'away' than are in the queue
                if (this.away < totalInQueue) {
                    this.away++;
                }
            } else {
                // If already at max occupancy, we don't increment away.
                // The frontend should handle showing they are in the waiting list.
            }
        } else {
            throw new IllegalStateException("Queue is empty");
        }
    }

    /**
     * @return - initialize the queue
     */
    public static BathroomQueue[] init(String teacherEmail) {
        ArrayList<BathroomQueue> queues = new ArrayList<>();
        queues.add(new BathroomQueue(teacherEmail, ""));
        return queues.toArray(new BathroomQueue[0]);
    }
}