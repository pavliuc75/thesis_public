# Camunda Spring Boot — Absence Request Example

This is a **Camunda Spring Boot** example that runs the **Absence Request** process. It uses Camunda-generated assets (BPMN, forms, config, templates) produced for the AbsenceRequest case.

## Project layout

All generated files go into `**src/main/resources/`**. The only exception is HTML forms: they go under `**src/main/resources/static/forms/**` (e.g. `request_absence_form.html`, `approve_absence_form.html`, `provide_further_explanation_form.html`). Everything else (BPMN, config, templates, dmn) lives directly in `resources` — e.g. `diagram_1.bpmn`, `config.json`, `*.ftl`, `*.json`.

- **Application entry:** `WebappExampleProcessApplication.java`  
It is configured to start a process instance by the **process key from the BPMN diagram** (`absenceRequest`) on deployment via:
  ```java
  runtimeService.startProcessInstanceByKey("absenceRequest");
  ```

## Prerequisites

- **Java 17+**
- **Maven 3.6+**

## How to start the application

1. **From the project root** (where `pom.xml` is):
  ```bash
   mvn spring-boot:run
  ```
2. **Or build and run the JAR:**
  ```bash
   mvn clean package
   java -jar target/loan-approval-spring-boot-0.0.1-SNAPSHOT.jar
  ```
3. **Access the Camunda web apps:**
  - **Cockpit / Tasklist / Admin:**  
   [http://localhost:8081/camunda](http://localhost:8081/camunda)  
  - Login: `demo` / `demo`

After startup, one process instance of **Absence Request** is started automatically. Use the Tasklist to work on the user tasks (request absence, approve, provide further explanation, etc.).