package dk.digitalidentity.mvc.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.service.StatisticsService;

@RequireSupporter
@Controller
public class AdminIndexController {
	@Autowired
	private StatisticsService statisticsService;
	
	@GetMapping("/admin") 
	public String index(Model model) {
		model.addAttribute("lastHourLogins", statisticsService.getLoginCountLastHour());
		model.addAttribute("lastHourTotalLogins", statisticsService.getTotalLoginCountLastHour());
		model.addAttribute("yesterdayLogins", statisticsService.getLoginCountYesterday());
		model.addAttribute("yesterdayTotalLogins", statisticsService.getTotalLoginCountYesterday());
		model.addAttribute("personCount", statisticsService.getPersonCount());
		model.addAttribute("aprovedConditionsCount", statisticsService.getAprovedConditionCount());
		return "admin/index";
	}
}
