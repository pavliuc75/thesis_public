
package com.company.model;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bonitasoft.engine.bdm.lazy.LazyLoaded;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;


/**
 * 
 */
@javax.persistence.Entity(name = "AbsenceRequest")
@Table(name = "ABSENCEREQUEST")
@NamedQueries({
    @NamedQuery(name = "AbsenceRequest.findByPersistenceId", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.persistenceId= :persistenceId\n"),
    @NamedQuery(name = "AbsenceRequest.findByRequestId", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.requestId= :requestId\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.findByEmployeeId", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.employeeId= :employeeId\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.findByAbsenceType", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.absenceType= :absenceType\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.findByStartDate", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.startDate= :startDate\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.findByEndDate", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.endDate= :endDate\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.findByReason", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.reason= :reason\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.findByManagerId", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.managerId= :managerId\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.findByStatus", query = "SELECT a\nFROM AbsenceRequest a\nWHERE a.status= :status\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.find", query = "SELECT a\nFROM AbsenceRequest a\nORDER BY a.persistenceId"),
    @NamedQuery(name = "AbsenceRequest.countForFindByRequestId", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.requestId= :requestId\n"),
    @NamedQuery(name = "AbsenceRequest.countForFindByEmployeeId", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.employeeId= :employeeId\n"),
    @NamedQuery(name = "AbsenceRequest.countForFindByAbsenceType", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.absenceType= :absenceType\n"),
    @NamedQuery(name = "AbsenceRequest.countForFindByStartDate", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.startDate= :startDate\n"),
    @NamedQuery(name = "AbsenceRequest.countForFindByEndDate", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.endDate= :endDate\n"),
    @NamedQuery(name = "AbsenceRequest.countForFindByReason", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.reason= :reason\n"),
    @NamedQuery(name = "AbsenceRequest.countForFindByManagerId", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.managerId= :managerId\n"),
    @NamedQuery(name = "AbsenceRequest.countForFindByStatus", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\nWHERE a.status= :status\n"),
    @NamedQuery(name = "AbsenceRequest.countForFind", query = "SELECT COUNT(a)\nFROM AbsenceRequest a\n")
})
public class AbsenceRequest implements org.bonitasoft.engine.bdm.Entity
{

    @Id
    @GeneratedValue(generator = "default_bonita_seq_generator")
    @GenericGenerator(name = "default_bonita_seq_generator", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
        @Parameter(name = "sequence_name", value = "hibernate_sequence")
    })
    private Long persistenceId;
    @Version
    private Long persistenceVersion;
    @Column(name = "REQUESTID", nullable = true)
    private Integer requestId;
    @Column(name = "EMPLOYEEID", nullable = true)
    private Integer employeeId;
    @Column(name = "ABSENCETYPE", nullable = true, length = 255)
    private String absenceType;
    @Column(name = "STARTDATE", nullable = true)
    private Long startDate;
    @Column(name = "ENDDATE", nullable = true)
    private Long endDate;
    @Column(name = "REASON", nullable = true, length = 255)
    private String reason;
    @Column(name = "MANAGERID", nullable = true)
    private Integer managerId;
    @Column(name = "STATUS", nullable = true, length = 255)
    private String status;
    @OneToOne(orphanRemoval = true, optional = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "CLARIFICATIONMESSAGE_PID", foreignKey = @ForeignKey(name = "FK_2146357394"))
    @JsonIgnore
    private ClarificationMessage clarificationMessage;

    public AbsenceRequest() {
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

    public void setRequestId(Integer requestId) {
        this.requestId = requestId;
    }

    public Integer getRequestId() {
        return requestId;
    }

    public void setEmployeeId(Integer employeeId) {
        this.employeeId = employeeId;
    }

    public Integer getEmployeeId() {
        return employeeId;
    }

    public void setAbsenceType(String absenceType) {
        this.absenceType = absenceType;
    }

    public String getAbsenceType() {
        return absenceType;
    }

    public void setStartDate(Long startDate) {
        this.startDate = startDate;
    }

    public Long getStartDate() {
        return startDate;
    }

    public void setEndDate(Long endDate) {
        this.endDate = endDate;
    }

    public Long getEndDate() {
        return endDate;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setManagerId(Integer managerId) {
        this.managerId = managerId;
    }

    public Integer getManagerId() {
        return managerId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setClarificationMessage(ClarificationMessage clarificationMessage) {
        this.clarificationMessage = clarificationMessage;
    }

    @LazyLoaded
    public ClarificationMessage getClarificationMessage() {
        return clarificationMessage;
    }

}
