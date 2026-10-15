package com.open.spring.mvc.bathroom;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Convert;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.vladmihalcea.hibernate.type.json.JsonType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Teacher is a POJO, Plain Old Java Object.
 * --- @Data is Lombox annotation for @Getter @Setter @ToString @EqualsAndHashCode @RequiredArgsConstructor
 * --- @AllArgsConstructor is Lombox annotation for a constructor with all arguments
 * --- @NoArgsConstructor is Lombox annotation for a constructor with no arguments
 * --- @Entity annotation is used to mark the class as a persistent Java class.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Convert(attributeName ="teacher", converter = JsonType.class)
public class Teacher {

    /** automatic unique identifier for Teacher record
     * --- Id annotation is used to specify the identifier property of the entity.
     * ----GeneratedValue annotation is used to specify the primary key generation strategy to use.
     * ----- The strategy is to have the persistence provider pick an appropriate strategy for the particular database.
     * ----- GenerationType.AUTO is the default generation type and it will pick the strategy based on the used database.
     */ 
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /** name, dob are attributes to describe the teacher
     * --- @NonNull annotation is used to generate a constructor with AllArgsConstructor Lombox annotation.
     * --- @Size annotation is used to validate that the annotated field is between the specified boundaries, in this case between 2 and 30 characters.
     * --- @DateTimeFormat annotation is used to declare a field as a date, in this case the pattern is specified as yyyy-MM-dd.
     */ 
    @NonNull
    @Size(min = 2, max = 30, message = "First Name (2 to 30 chars)")
    private String firstname;

    @NonNull
    @Size(min = 2, max = 30, message = "Last Name (2 to 30 chars)")
    private String lastname;
    
        /** stats is used to store JSON for daily stat$
         * --- @JdbcTypeCode annotation is used to specify the JDBC type code for a column, in this case json.
         * --- @Column annotation is used to specify the mapped column for a persistent property or field, in this case columnDefinition is specified as jsonb.
         * * * Example of JSON data:
            "stats": {
                "2022-11-13": {
                    "calories": 2200,
                    "steps": 8000
                }
            }
        */
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "json")
        private Map<String,Map<String, Object>> stats = new HashMap<>(); 
        
    
        /** Custom constructor for Teacher when building a new Teacher object from an API call
         */
        public Teacher(String firstname, String lastname) {
            this.firstname = firstname;
            this.lastname = lastname;
        }
    
    
        /** 1st telescoping method to create a Teacher object with USER role
         * @param first_name
         * @param last_name
         *  */ 
        public static Teacher createTeacher(String firstname, String lastname) {
            // By default, Spring Security expects roles to have a "ROLE_" prefix.
            Teacher teacher = new Teacher();
            teacher.setFirstname(firstname);
            teacher.setLastname(lastname);
        return teacher;
    }

   
    /** Static method to initialize an array list of Teacher objects 
     * @return Teacher[], an array of Teacher objects
     */
    public static ArrayList<Teacher> init() {
        ArrayList<Teacher> teachers = new ArrayList<>();
        // Generic sample teachers; replace with your school's staff list.
        teachers.add(createTeacher("Ada", "Lovelace"));
        teachers.add(createTeacher("Grace", "Hopper"));
        teachers.add(createTeacher("Alan", "Kay"));
        teachers.add(createTeacher("Katherine", "Johnson"));

        return teachers;
    }

    /** Static method to print Teacher objects from an array
     * @param args, not used
     */
    public static void main(String[] args) {
        // obtain Teacher from initializer
        ArrayList<Teacher> teachers = init();

        // iterate using "enhanced for loop"
        for( Teacher teacher : teachers) {
            System.out.println(teacher);  // print object
        }
    }


    public void setEmail(String string) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setEmail'");
    }

}