
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
@javax.persistence.Entity(name = "ClarificationMessage")
@Table(name = "CLARIFICATIONMESSAGE")
@NamedQueries({
    @NamedQuery(name = "ClarificationMessage.findByPersistenceId", query = "SELECT c\nFROM ClarificationMessage c\nWHERE c.persistenceId= :persistenceId\n"),
    @NamedQuery(name = "ClarificationMessage.findByMessage", query = "SELECT c\nFROM ClarificationMessage c\nWHERE c.message= :message\nORDER BY c.persistenceId"),
    @NamedQuery(name = "ClarificationMessage.findByTimestampp", query = "SELECT c\nFROM ClarificationMessage c\nWHERE c.timestampp= :timestampp\nORDER BY c.persistenceId"),
    @NamedQuery(name = "ClarificationMessage.find", query = "SELECT c\nFROM ClarificationMessage c\nORDER BY c.persistenceId"),
    @NamedQuery(name = "ClarificationMessage.countForFindByMessage", query = "SELECT COUNT(c)\nFROM ClarificationMessage c\nWHERE c.message= :message\n"),
    @NamedQuery(name = "ClarificationMessage.countForFindByTimestampp", query = "SELECT COUNT(c)\nFROM ClarificationMessage c\nWHERE c.timestampp= :timestampp\n"),
    @NamedQuery(name = "ClarificationMessage.countForFind", query = "SELECT COUNT(c)\nFROM ClarificationMessage c\n"),
    @NamedQuery(name = "ClarificationMessage.findClarificationMessageByAbsenceRequestPersistenceId", query = "SELECT clarificationmessage_1 FROM AbsenceRequest absencerequest_0 JOIN absencerequest_0.clarificationMessage as clarificationmessage_1 WHERE absencerequest_0.persistenceId= :persistenceId")
})
public class ClarificationMessage implements org.bonitasoft.engine.bdm.Entity
{

    @Id
    @GeneratedValue(generator = "default_bonita_seq_generator")
    @GenericGenerator(name = "default_bonita_seq_generator", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
        @Parameter(name = "sequence_name", value = "hibernate_sequence")
    })
    private Long persistenceId;
    @Version
    private Long persistenceVersion;
    @Column(name = "MESSAGE", nullable = true, length = 255)
    private String message;
    @Column(name = "TIMESTAMPP", nullable = true)
    private Long timestampp;

    public ClarificationMessage() {
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

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setTimestampp(Long timestampp) {
        this.timestampp = timestampp;
    }

    public Long getTimestampp() {
        return timestampp;
    }

}
