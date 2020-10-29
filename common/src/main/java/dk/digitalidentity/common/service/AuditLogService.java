package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.model.AuditLog;

@Service
public class AuditLogService {

	@Autowired
	private AuditLogDao auditLogDao;

	public AuditLog findById(Long id) {
		return auditLogDao.getById(id);
	}
	
	public List<AuditLog> findByCorrelationId(String id) {
		return auditLogDao.findByCorrelationId(id);
	}
	
	public List<AuditLog> findAllFromLastWeek() {
		return auditLogDao.findAllByTtsAfter(LocalDateTime.now().minus(7, ChronoUnit.DAYS));
	}
}
