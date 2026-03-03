# Bonita Project Files Example

Example project using generated files for the **Absence Request** case.

**Bonita Community Edition**  
Version: **2025.2**

## Setup Instructions

1. Install Bonita Studio: https://www.bonitasoft.com/downloads

2. Create a new project. Then, in the left sidebar, right-click the project root and select **Show in system explorer**.

3. Replace files with the generated ones using the mapping below.

## File Placement Mapping

| Generated file | Target location |
|---|---|
| `approveAbsenceForm` | `/app/web_page/` |
| `provideFurtherExplanationForm` | `/app/web_page/` |
| `requestAbsenceForm` | `/app/web_page/` |
| `.index.json` | `/app/web_page/.metadata/` |
| `_ZvkdoO7sEfCoIMCqMDCGog.conf` | `/app/process_configurations/` |
| `bom.xml` | `bdm/` |
| `diagram_1-1.0.proc` | `app/diagrams/` |
| `Organization.xml` | `app/` |

> If `.index.json` is auto-generated after inserting the three form folders, do **not** replace it.

4. Return to Bonita Studio. In the left sidebar, right-click `Organization.organization` and select **Deploy**. Do the same for `bom.xml`.

5. Open `diagram_1-1.0.proc` and click **Run** in the top bar.

	If you get an error saying REST or Email connectors are missing, install them from the Bonita Marketplace:
	- Top bar: **Overview**
	- Select **Open Marketplace**
	- Install: **Email**, **REST**, and **Groovy**

## Notes

- When switching between users (for example, `alice` and `bob`), the password is `bdm` for every user.