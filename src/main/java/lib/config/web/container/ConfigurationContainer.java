package lib.config.web.container;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lib.config.base.configuration.Configuration;
import lib.config.web.DisplayableConfiguration;

import org.simpleframework.http.Cookie;
import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the web interface, and parses incoming commands.
 * 
 * @author Benjamin Leov
 *
 */
public class ConfigurationContainer implements Container {
	private static final Logger logger = LoggerFactory
			.getLogger(ConfigurationContainer.class);

	private final Map<String, DisplayableConfiguration> config;
	private final Set<ContainerListener> listeners;

	/**
	 * 
	 * 
	 * @param config
	 *            Displayable configurations, hashed on an Id. This does not
	 *            have to be the id of the configuration.
	 */
	public ConfigurationContainer(Map<String, DisplayableConfiguration> config) {
		this.config = config;
		this.listeners = new HashSet<ContainerListener>();
	}

	@Override
	public void handle(Request request, Response response) {
		try {

			long time = System.currentTimeMillis();

			response.setContentType("text/html");
			// response.set("Server", "ConfigurationServer/1.0 (Simple 4.0)");
			response.setDate("Date", time);
			response.setDate("Last-Modified", time);

			PrintStream body = response.getPrintStream();
			
			body.println("<html>");
			body.println("<title>Simple Configuration Server</title>");

			Query query = request.getAddress().getQuery();
			StringBuilder html = new StringBuilder();
			
			// command/navigation request
			if (request.getMethod().equalsIgnoreCase("GET")) {
				Query postQuery = request.getQuery();

				Command command = parseCommand(postQuery);

				// default index page
				if (query.isEmpty()) {

					// display a list of all the configurations

					html.append("<h2>Configurations</h2>");
					html.append("<ul>");

					for (String key : config.keySet()) {

						DisplayableConfiguration curr = config.get(key);
						html.append("<li>");
						html.append("<a href='?config=");
						html.append(key);
						html.append("'>");
						html.append(curr.getDisplayName());
						html.append("</a>");
						html.append("</li>");
					}

					html.append("</ul>");

					appendCommandsForm(html, null, Command.EXIT);

				} else {

					String queried = query.get("config");

					System.out.println(query.toString());
					DisplayableConfiguration curr = config.get(queried);
					
					if (curr != null) {

						switch (command) {
						case ADD:
							appendAddForm(html, curr);
							break;
						case UPDATE:
							// TODO display update form
							html.append("Display update form");
							// appendUpdateForm(html, curr);
							break;
						case DELETE:
							// TODO display delete form
							html.append("Dispaly delete form");
							// appendDeleteForm(html, curr);
							break;
						case EXIT:
							notifyOnCommand(Command.EXIT);
							html.append("Server has now stopped.");
							break;
						case VIEW:
						default:
							appendConfigForm(html, curr);
							appendAllCommandsForm(html, queried);
							break;
						}
					} else {
						// unknown request
						appendError(html,
								"Cannot find config with that identifier.");
					}
				}

			} else if (request.getMethod().equalsIgnoreCase("POST")) {
				// a post to update a setting

				Query postQuery = request.getQuery();
				Command command = parseCommand(postQuery);

				String id = postQuery.get("config_id");

				DisplayableConfiguration conf = config.get(id);

				if (conf == null) {
					appendError(html,
							"Invalid command received. No config specified.");
				} else {

					boolean modified = false;
					String modifyedKey = null;

					switch (command) {
					case DELETE:

						for (String key : postQuery.keySet()) {
							if (conf.removeProperty(key)) {
								notifyOnDelete(conf, key);
							}
						}

						html.append("Configuration has been updated!");
						html.append("<a href='/'>Back</a>");

						break;
					case ADD:
						String key = postQuery.get("key");
						String value = postQuery.get("value");
						
						conf.setProperty(key, value);
						notifyOnAdd(conf, key);
						html.append("Configuration has been added!");
						html.append("<a href='/'>Back</a>");
						break;
					case UPDATE:
						
						for (String currKey : postQuery.keySet()) {
							if (conf.hasProperty(currKey)) {
								conf.setProperty(currKey, postQuery.get(currKey));
								notifyOnUpdate(conf, currKey);
							}
						}
						
						html.append("Configuration has been updated!");
						html.append("<a href='/'>Back</a>");
						break;
					
					default:
						appendError(html, "Invalid command received.");
						break;
					}
				}
			}

			body.print(html.toString());
			body.println("</html>");

			body.close();
		} catch (IOException e) {
			logger.warn("IOException occured on handle.", e);
		}
	}

	private void appendAddForm(StringBuilder html, DisplayableConfiguration curr) {

		appendConfigForm(html, curr);

		html.append("<h3>Add Setting</h3>");

		html.append("<form action='.' method='post'>\n");

		html.append("<input type='hidden' name='command' value='"
				+ Command.ADD.toString() + "' />");

		// add the config id into the form
		html.append("<input type='hidden' name='config_id' value='");
		html.append(curr.getId());
		html.append("' />\n");
		
		// add form
		html.append("<input type='text' name='key' value='' />");
		html.append("<input type='text' name='value' value='' />");
		html.append("<input type='submit' />");

		html.append("</form>");

	}

	private Command parseCommand(Query query) {

		String commandStr = query.get("command");

		Command command = null;
		if (commandStr != null) {
			command = Command.valueOf(commandStr);
		}

		// default command, if not specified, is view
		if (command == null) {
			command = Command.VIEW;
		}

		return command;
	}

	private void appendError(StringBuilder html, String errorMessage) {
		html.append("<h2>Error</h2>");
		html.append("<p>" + errorMessage + "</p>");
	}

	private void appendConfigForm(StringBuilder html,
			DisplayableConfiguration config) {
		
		html.append("<h3>");
		html.append("Configuration Form for ");
		html.append(config.getDisplayName());
		html.append("</h3>");
		html.append("<form action='.' method='post'>\n");

		html.append("<input type='hidden' name='command' value='"
				+ Command.UPDATE.toString() + "' />");

		// add the config id into the form
		html.append("<input type='hidden' name='config_id' value='");
		html.append(config.getId());
		html.append("' />\n");

		for (String key : config.getKeys()) {
			html.append("<br />");
			html.append("<label>");
			html.append(key);
			html.append("</label>");
			html.append("<input type='text' name='" + key + "' ");

			String value = config.getProperty(key);

			if (value != null) {
				html.append("value='");
				html.append(value);
				html.append("'");
			}

			html.append("/>");
		}
		html.append("<br />");
		html.append("<input type='submit'/>");

		html.append("</form>");

	}

	private void appendCommandsForm(StringBuilder html, String configId, Command... commands) {

		html.append("<h2>Server Commands</h2>");

		// VIEW is an alias for a standard get request, so doesn't need to be
		// displayed
		// to the user
		for (Command curr : commands) {

			if (curr != Command.VIEW) {
				
				html.append("<form action='.' method='get'>");
				html.append("<input type='hidden' name='command' value='"
						+ curr.toString() + "' />");
				
				if(configId != null) {
					html.append("<input type='hidden' name='config' value='"
							+ configId + "' />");
				}
				
				html.append("<input type='submit' value='" + curr.toString()
						+ "' />");
				html.append("</form>");
			}
		}
	}

	private void appendAllCommandsForm(StringBuilder html, String parameters) {
		appendCommandsForm(html, parameters, Command.values());
	}

	public void addListener(ContainerListener listener) {
		listeners.add(listener);
	}

	private void notifyOnAdd(Configuration config, String key) {
		for (ContainerListener curr : listeners) {
			curr.onAdd(config, key);
		}
	}

	private void notifyOnDelete(Configuration config, String key) {
		for (ContainerListener curr : listeners) {
			curr.onDelete(config, key);
		}
	}

	private void notifyOnUpdate(Configuration config, String key) {
		for (ContainerListener curr : listeners) {
			curr.onModifed(config, key);
		}
	}

	private void notifyOnCommand(Command command) {
		for (ContainerListener curr : listeners) {
			curr.onCommand(command);
		}
	}

}
