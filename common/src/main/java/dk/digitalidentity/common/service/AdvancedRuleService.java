package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Person;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdvancedRuleService {

	public String lookupField(Person person, String personField) {
		String attribute = null;
		
        switch (personField) {
            case "userId":
            case "sAMAccountName": // TODO: this is deprecated, but we are keeping it to support existing SPs setup with this value until they are migrated
                attribute = PersonService.getUsername(person);
                break;
            case "uuid":
                attribute = person.getUuid();
                break;
            case "cpr":
                attribute = person.getCpr();
                break;
            case "name":
            	attribute = person.getName();
            	break;
            case "alias":
            	attribute = person.getNameAlias();
            	break;
            case "email":
            	attribute = person.getEmail();
            	break;
            case "firstname":
            	try {
            		int idx = person.getName().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getName().substring(0, idx);
            		}
            		else {
            			attribute = person.getName();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse name on " + person.getId(), ex);
            		attribute = person.getName();
            	}
            	break;
            case "lastname":
            	try {
            		int idx = person.getName().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getName().substring(idx + 1);
            		}
            		else {
            			attribute = person.getName();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse name on " + person.getId(), ex);
            		attribute = person.getName();
            	}
            	break;
            default:
                if (person.getAttributes() != null) {
                    attribute = person.getAttributes().get(personField);
                }
        }
        
        return attribute;
	}
	
	/*
	 * Simple examples
	 * ===============
	 * JOIN(VALUE(user.name), VALUE(' '), VALUE(user.userId))   ->   "Brian Graversen bsg"
	 * UPPER(VALUE(user.userId))                                ->   "BSG"
	 * LOWER(VALUE(user.name))                                  ->   "brian graversen"
	 * 
	 * Regex replacement example
	 * =========================
	 * REGEX_REPLACE(VALUE(user.name), '\s', VALUE('_'))        ->   "Brian_Graversen"   
	 * 
	 * Combined example
	 * ================
	 * JOIN(
     *   UPPER(
     *     JOIN(
     *       VALUE(user.userId),
     *       VALUE(', '),
     *       REGEX_REPLACE(
     *         VALUE(user.name),
     *         '\s',
     *         VALUE('_')
     *       )
     *    )
     *   ),
     *   LOWER(VALUE(' xXx ')),
     *   UPPER(VALUE(user.email))
     * )
     * 
     * Above outputs "BSG BRIAN_GRAVERSEN xxx BSG@DIGITAL-IDENTITY.DK"
	 */
	
	public String evaluateRule(String rule, Person person) throws EvaluationException {
		
		// trim before evaluating
		rule = rule.trim();
		
		CommandAndArgument commandAndArgument = extractCommandAndArgument(rule);
		
		switch (commandAndArgument.command) {
			case "VALUE":
				return evaluateValue(commandAndArgument.argument, person);
			case "UPPER":
				return evaluateUpper(commandAndArgument.argument, person);
			case "LOWER":
				return evaluateLower(commandAndArgument.argument, person);
			case "JOIN":
				return evaluateJoin(commandAndArgument.argument, person);
			case "REGEX_REPLACE":
				return evaluateRegex(commandAndArgument.argument, person);
			default:
				log.error("Should not get here, it should have been handled in extractCommandAndArgument: " + commandAndArgument.command);
				throw new EvaluationException("Syntaksfejl: Ukendt operation '" + commandAndArgument.command + "'");
		}
	}
	
	private String evaluateRegex(String argument, Person person) throws EvaluationException {
		List<String> tokens = tokenize(argument);
		if (tokens.size() != 3) {
			throw new EvaluationException("REGEX_REPLACE tager 3 parametre");
		}
		
		String source = evaluateRule(tokens.get(0), person);
		String replacement = evaluateJoin(tokens.get(2), person);
		String regEx = tokens.get(1).trim().replace("'", "").trim();

		return source.replaceAll(regEx, replacement);
	}

	private String evaluateJoin(String argument, Person person) throws EvaluationException {
		StringBuilder builder = new StringBuilder();
		
		List<String> tokens = tokenize(argument);
		for (String token : tokens) {
			token = token.trim();
			if (!StringUtils.hasLength(token)) {
				continue;
			}
			
			builder.append(evaluateRule(token, person));
		}

		return builder.toString();
	}

	private List<String> tokenize(String argument) {
		List<String> result = new ArrayList<>();
		
		StringBuilder builder = new StringBuilder();
		int counter = 0;
		boolean inString = false;
		
		for (char c : argument.toCharArray()) {
			if (c == '(') {
				if (!inString) {
					counter++;
				}
			}
			else if (c == ')') {
				if (!inString) {
					counter--;
				}
			}
			else if (c == '\'') {
				inString = !inString;
			}
			else if (c == ',') {
				if (counter == 0) {
					result.add(builder.toString().trim());
					builder = new StringBuilder();
					
					continue;
				}
			}
			
			builder.append(c);
		}
		
		if (builder.length() > 0) {
			result.add(builder.toString().trim());
		}
		
		return result;
	}

	private String evaluateLower(String argument, Person person) throws EvaluationException {
		String value = evaluateRule(argument.trim(), person);
		
		return value.toLowerCase();
	}

	private String evaluateUpper(String argument, Person person) throws EvaluationException {
		String value = evaluateRule(argument.trim(), person);
		
		return value.toUpperCase();
	}

	private String evaluateValue(String argument, Person person) throws EvaluationException {
		if (argument.startsWith("'")) {
			int stop = argument.lastIndexOf("'");
			if (stop < 1) {
				throw new EvaluationException("Syntaksfejl: Input til VALUE skal indeholde både en start og stop ' karakter for faste værdier");
			}
			
			return argument.substring(1, stop);
		}
		else if (argument.startsWith("user.")) {
			argument = argument.substring(5);

			String value = lookupField(person, argument);
			if (value == null) {
				return "";
			}

			return value;
		}
		
		throw new EvaluationException("Syntaksfejl: Input til VALUE ikke lovligt '" + argument + "'");
	}

	private CommandAndArgument extractCommandAndArgument(String rule) throws EvaluationException {
		try {
			int first = rule.indexOf("(");
			int last = rule.lastIndexOf(")");
			
			// need at least 1 character in front of the first ( so we have a command
			if (first < 1) {
				log.warn("Missing start parantheses for rule: " + rule);
				throw new EvaluationException("Syntaksfejl: Reglen indeholder ikke nogen operation, eller mangler en start-parantes");
			}
			
			if (last < first) {
				log.warn("Missing stop parantheses for rule: " + rule);
				throw new EvaluationException("Syntaksfejl: Reglen mangler en slut-parantes");
			}
			
			CommandAndArgument result = new CommandAndArgument();
			result.command = rule.substring(0, first);
			result.argument = rule.substring(first + 1, last).trim();

			if (!knownCommand(result.command)) {
				throw new EvaluationException("Syntaksfejl: Ukendt operation '" + result.command + "'");				
			}
			
			if (!StringUtils.hasLength(result.argument)) {
				throw new EvaluationException("Syntaksfejl: Operationen '" + result.command + "' har ikke noget input");
			}

			return result;
		}
		catch (Exception ex) {
			if (ex instanceof EvaluationException) {
				throw ex;
			}

			log.warn("Failed to parse rule: " + ex.getMessage());
			throw new EvaluationException("Teknisk fejl: " + ex.getMessage());
		}
	}

	private boolean knownCommand(String command) {
		switch (command) {
			case "REGEX_REPLACE":
			case "UPPER":
			case "LOWER":
			case "JOIN":
			case "VALUE":
				return true;
			default:
				return false;
		}
	}

	class CommandAndArgument {
		String command;
		String argument;
	}
}