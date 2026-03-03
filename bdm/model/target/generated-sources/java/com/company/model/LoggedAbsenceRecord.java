
package com.company.model;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Version;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;


/**
 * 
 */
@javax.persistence.Entity(name = "LoggedAbsenceRecord")
@Table(name = "LOGGEDABSENCERECORD")
@NamedQueries({
    @NamedQuery(name = "LoggedAbsenceRecord.findByPersistenceId", query = "SELECT l\nFROM LoggedAbsenceRecord l\nWHERE l.persistenceId= :persistenceId\n"),
    @NamedQuery(name = "LoggedAbsenceRecord.findByRecordId", query = "SELECT l\nFROM LoggedAbsenceRecord l\nWHERE l.recordId= :recordId\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LoggedAbsenceRecord.findByAbsenceRequestId", query = "SELECT l\nFROM LoggedAbsenceRecord l\nWHERE l.absenceRequestId= :absenceRequestId\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LoggedAbsenceRecord.findByLoggedDate", query = "SELECT l\nFROM LoggedAbsenceRecord l\nWHERE l.loggedDate= :loggedDate\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LoggedAbsenceRecord.find", query = "SELECT l\nFROM LoggedAbsenceRecord l\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LoggedAbsenceRecord.countForFindByRecordId", query = "SELECT COUNT(l)\nFROM LoggedAbsenceRecord l\nWHERE l.recordId= :recordId\n"),
    @NamedQuery(name = "LoggedAbsenceRecord.countForFindByAbsenceRequestId", query = "SELECT COUNT(l)\nFROM LoggedAbsenceRecord l\nWHERE l.absenceRequestId= :absenceRequestId\n"),
    @NamedQuery(name = "LoggedAbsenceRecord.countForFindByLoggedDate", query = "SELECT COUNT(l)\nFROM LoggedAbsenceRecord l\nWHERE l.loggedDate= :loggedDate\n"),
    @NamedQuery(name = "LoggedAbsenceRecord.countForFind", query = "SELECT COUNT(l)\nFROM LoggedAbsenceRecord l\n")
})
public class LoggedAbsenceRecord implements org.bonitasoft.engine.bdm.Entity
{

    @Id
    @GeneratedValue(generator = "default_bonita_seq_generator")
    @GenericGenerator(name = "default_bonita_seq_generator", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
        @Parameter(name = "sequence_name", value = "hibernate_sequence")
    })
    private Long persistenceId;
    @Version
    private Long persistenceVersion;
    @Column(name = "RECORDID", nullable = true)
    private Integer recordId;
    @Column(name = "ABSENCEREQUESTID", nullable = true)
    private Integer absenceRequestId;
    @Column(name = "LOGGEDDATE", nullable = true)
    private Long loggedDate;

    public LoggedAbsenceRecord() {
    }

    public void setPersistenceId(Long persistenceId) {
        this.persistenceId = persistenceId;
    }

    public Long getPersistenceId() {
        return persistenceId;
    }

    public void setPersistenceVersion(Long persistenceVersion) {
        this.persistenceVersion = persistenceVersion;
    }

    public Long getPersistenceVersion() {
        return persistenceVersion;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    public Integer getRecordId() {
        return recordId;
    }

    public void setAbsenceRequestId(Integer absenceRequestId) {
        this.absenceRequestId = absenceRequestId;
    }

    public Integer getAbsenceRequestId() {
        return absenceRequestId;
    }

    public void setLoggedDate(Long loggedDate) {
        this.loggedDate = loggedDate;
    }

    public Long getLoggedDate() {
        return loggedDate;
    }

}
