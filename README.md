# BPMS Generator

Generates Camunda or Bonita artifacts from BPMN, config, forms, and other model files.

## Prerequisites

- **Java 17**
- **Maven**

## Build

From the project root (where `pom.xml` is):

```bash
mvn -q -DskipTests package
```

This produces `target/bpms_generator-0.0.1.jar`.

## Run

**Default (Camunda):**

```bash
java -jar target/bpms_generator-0.0.1.jar
```

**Explicit target:**

```bash
# Camunda
java -jar target/bpms_generator-0.0.1.jar camunda

# Bonita
java -jar target/bpms_generator-0.0.1.jar bonita
```

The first argument is optional and case-insensitive. Only `camunda` and `bonita` are valid; anything else is ignored and the run uses the default (Camunda).

## Output

Generated files are written under:

- **Camunda:** `src/main/resources/out/camunda/`
- **Bonita:** `src/main/resources/out/bonita/`

Input models are read from `src/main/resources/models/` and related paths (see `Main.java` for the exact list).

## Using a different model set

1. **Update the models folder**
  Put your platform-independent model set under `src/main/resources/models/` (or adjust paths in code as needed). The following are **mandatory**:
  - **Config file** – process configuration (e.g. lanes, tasks, forms, email/REST/DMN refs).
  - **Organization file** – organization/actors (e.g. Archimate).
  - **Process BPMN file** – the process diagram (BPMN 2.0).
  - **Process PROC file** – Bonita’s process format. To obtain it: import the BPMN into Bonita Studio (it is converted to `.proc`), then extract that file from the Bonita project and place it in your models folder.
2. **Update paths in `Main.java`**
  In `src/main/java/generator/Main.java`, adjust the path constants in the “Paths to various model files” section so they point to your files:

  | Variable            | Description                      | Example                                                                       |
  | ------------------- | -------------------------------- | ----------------------------------------------------------------------------- |
  | `bpmnFilePath`      | Process BPMN                     | `src/main/resources/models/diagram_1.bpmn`                                    |
  | `procFilePath`      | Process PROC (Bonita)            | `src/main/resources/models/diagram_1-1.0.proc`                                |
  | `processConfigPath` | Config JSON                      | `src/main/resources/models/config.json`                                       |
  | `organizationPath`  | Organization model               | `models/organization.archimate`                                               |
  | `statesPath`        | Business object states           | `src/main/resources/models/states.json`                                       |
  | `businessDataPath`  | Business objects (PUML)          | `src/main/resources/models/business_objects.puml`                             |
  | `formPaths`         | Form JSON files                  | `List.of("models/form/...", ...)`                                             |
  | `restActionPaths`   | REST action JSON files           | `List.of("models/rest/...", ...)`                                             |
  | `emailConfigsPaths` | Email configs (FTL + JSON pairs) | `List.of(new String[]{"models/email/....ftl", "models/email/....json"}, ...)` |
  | `dmnPaths`          | DMN files (if any)               | `List.of("models/dmn/....dmn")`                                               |

   Rebuild and run as above; the generator will use your new model set and write output to `out/camunda/` or `out/bonita/` accordingly.

**Deploying the generated artifacts**  
The following branches contain example projects (Camunda Spring Boot and Bonita project files) that show how to use the generated files, plus guidelines for inserting them into your own setup:

- [Camunda — example project & guidelines](https://github.com/pavliuc75/thesis_public/tree/___camunda_example)
- [Bonita — example project & guidelines](https://github.com/pavliuc75/thesis_public/tree/___bonita_example)

