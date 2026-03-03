
package com.company.model;

import java.util.List;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;

public interface AbsenceRequestDAO
    extends BusinessObjectDAO
{


    public AbsenceRequest findByPersistenceId(Long persistenceId);

    public List<AbsenceRequest> findByRequestId(Integer requestId, int startIndex, int maxResults);

    public List<AbsenceRequest> findByEmployeeId(Integer employeeId, int startIndex, int maxResults);

    public List<AbsenceRequest> findByAbsenceType(String absenceType, int startIndex, int maxResults);

    public List<AbsenceRequest> findByStartDate(Long startDate, int startIndex, int maxResults);

    public List<AbsenceRequest> findByEndDate(Long endDate, int startIndex, int maxResults);

    public List<AbsenceRequest> findByReason(String reason, int startIndex, int maxResults);

    public List<AbsenceRequest> findByManagerId(Integer managerId, int startIndex, int maxResults);

    public List<AbsenceRequest> findByStatus(String status, int startIndex, int maxResults);

    public List<AbsenceRequest> find(int startIndex, int maxResults);

    public Long countForFindByRequestId(Integer requestId);

    public Long countForFindByEmployeeId(Integer employeeId);

    public Long countForFindByAbsenceType(String absenceType);

    public Long countForFindByStartDate(Long startDate);

    public Long countForFindByEndDate(Long endDate);

    public Long countForFindByReason(String reason);

    public Long countForFindByManagerId(Integer managerId);

    public Long countForFindByStatus(String status);

    public Long countForFind();

    public AbsenceRequest newInstance();

}
