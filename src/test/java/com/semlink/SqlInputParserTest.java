package com.semlink;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlInputParserTest {

    @Test
    void buildSchemaStatistics_shouldExtractTableAndColumnStats() {
        String schemaSql = """
            CREATE TABLE students (
                student_id INT NOT NULL,
                full_nm VARCHAR(100),
                dept_name VARCHAR(50),
                brn_cd INT,
                FOREIGN KEY (brn_cd) REFERENCES departments(dept_id)
            );
            CREATE TABLE departments (
                dept_id INT NOT NULL,
                dept_name VARCHAR(100)
            );
            """;
        String dataSql = """
            INSERT INTO students (student_id, full_nm, dept_name, brn_cd) VALUES ('S001', 'Alice Gupta', 'Computer Science', 1);
            INSERT INTO students (student_id, full_nm, dept_name, brn_cd) VALUES ('S002', 'Bob Singh', 'Physics', 2);
            INSERT INTO departments (dept_id, dept_name) VALUES (1, 'CS');
            INSERT INTO departments (dept_id, dept_name) VALUES (2, 'PHY');
            """;

        SqlInputParser parser = new SqlInputParser();
        SqlInputParser.SchemaStatistics stats = parser.buildSchemaStatistics(schemaSql, dataSql);

        assertEquals("uploaded", stats.datasetName());
        assertEquals(2, stats.tables().size());

        SqlInputParser.TableStats students = stats.tables().stream()
            .filter(t -> t.name().equals("students")).findFirst().orElseThrow();
        assertEquals(4, students.columns().size());
        assertEquals("student_id", students.primaryKey());
        assertEquals(1, students.foreignKeys().size());
        assertEquals(2, students.rowCount());

        SqlInputParser.ColumnStats idCol = students.columns().stream()
            .filter(c -> c.name().equals("student_id")).findFirst().orElseThrow();
        assertFalse(idCol.nullable());
        assertTrue(idCol.sampleValues().contains("S001"));
        assertTrue(idCol.sampleValues().contains("S002"));
    }
}