
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
@javax.persistence.Entity(name = "LineManager")
@Table(name = "LINEMANAGER")
@NamedQueries({
    @NamedQuery(name = "LineManager.findByPersistenceId", query = "SELECT l\nFROM LineManager l\nWHERE l.persistenceId= :persistenceId\n"),
    @NamedQuery(name = "LineManager.findById", query = "SELECT l\nFROM LineManager l\nWHERE l.id= :id\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LineManager.findByFullName", query = "SELECT l\nFROM LineManager l\nWHERE l.fullName= :fullName\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LineManager.findByEmail", query = "SELECT l\nFROM LineManager l\nWHERE l.email= :email\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LineManager.find", query = "SELECT l\nFROM LineManager l\nORDER BY l.persistenceId"),
    @NamedQuery(name = "LineManager.countForFindById", query = "SELECT COUNT(l)\nFROM LineManager l\nWHERE l.id= :id\n"),
    @NamedQuery(name = "LineManager.countForFindByFullName", query = "SELECT COUNT(l)\nFROM LineManager l\nWHERE l.fullName= :fullName\n"),
    @NamedQuery(name = "LineManager.countForFindByEmail", query = "SELECT COUNT(l)\nFROM LineManager l\nWHERE l.email= :email\n"),
    @NamedQuery(name = "LineManager.countForFind", query = "SELECT COUNT(l)\nFROM LineManager l\n")
})
public class LineManager implements org.bonitasoft.engine.bdm.Entity
{

    @Id
    @GeneratedValue(generator = "default_bonita_seq_generator")
    @GenericGenerator(name = "default_bonita_seq_generator", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
        @Parameter(name = "sequence_name", value = "hibernate_sequence")
    })
    private Long persistenceId;
    @Version
    private Long persistenceVersion;
    @Column(name = "ID", nullable = true)
    private Integer id;
    @Column(name = "FULLNAME", nullable = true, length = 255)
    private String fullName;
    @Column(name = "EMAIL", nullable = true, length = 255)
    private String email;

    public LineManager() {
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

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

}
