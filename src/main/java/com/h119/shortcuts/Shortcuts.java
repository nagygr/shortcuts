package com.h119.shortcuts;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.filechooser.*;

import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class Shortcuts {
	private String home;

	private JComboBox<Application> applicationBox;
	private JEditorPane shortcutsText;
	private JScrollPane scroll;

	private static final int MARGIN = 10;

	public static String defaultConfig;
	public static String help;

	static {
		defaultConfig = """
			{
				"applications": [
					{
						"name": "i3",
						"config": ".config/i3/config",
						"syntax": "bindsym ([a-zA-Z0-9$+]+) (.*)"
					},
					{
						"name": "vim",
						"config": ".vimrc",
						"syntax": "(?:map|nmap|nnoremap|tnoremap) ((?:[a-zA-Z0-9<>]|\\\\p{Punct})+) (.*)"
					},
					{
						"name": "vifm",
						"config": ".config/vifm/vifmrc",
						"syntax": "nnoremap ([a-zA-Z0-9<>,]+) (.*)"
					}
				]
			}
			""";

		help = """
			Shortcuts
			=========

			This application parses application configs for keyboard shortcuts and echos them.
			Shortcuts require Java SDK 15+.
			
			It uses information given in its config file for each application:

			- the application name: it is used in the combo box to tell Shortcuts
				which application's config to look for
			- the config's path: the path to the config file (either absolute path
				or relative to the user's home directory
			- the syntax: the regex that matches the lines that define shortcuts
				- the regex must contain two unnamed capturing groups: the first is the keyboard
					shortcut, the second is the command that's executed
				- the regex can contain any number of additional non-capturing groups
					- Form: (?:regex)

			The config file's location is: $HOME/.config/shortcuts/shortcuts.conf.
			If it is not found there then the directory is created and a default config
			file is placed there.
			""";
	}

	public static class Application {
		private String name;
		private String config;
		private String syntax;

		public Application(String name, String config, String syntax) {
			this.name = name;
			this.config = config;
			this.syntax = syntax;
		}

		public String getName() {
			return name;
		}

		public String getConfig() {
			return config;
		}

		public String getSyntax() {
			return syntax;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private List<Application> applications;

	public Shortcuts() {
		shortcutsText = new JEditorPane();
		shortcutsText.setContentType("text/html");

		scroll = new JScrollPane(shortcutsText);

		applicationBox = new JComboBox<>();

		try {
			applications = new ArrayList<>();
			home = System.getProperty("user.home");
			Path configFile = Paths.get(home, ".config", "shortcuts", "shortcuts.conf");

			if (!Files.exists(configFile)) {
				System.out.format("Creating default config file (%s)\n", configFile);
				Files.createDirectories(Paths.get(home, ".config", "shortcuts"));
				Files.write(configFile, defaultConfig.getBytes());
			}

			String configuration = Files.readString(configFile);
			JSONException illegalConfigFile = null;

			JSONObject root = new JSONObject(configuration);

			JSONArray configs = root.getJSONArray("applications");
			int configsLength = configs.length();

			for (int i = 0; i < configsLength; ++i) {
				JSONObject configObject = configs.getJSONObject(i);
				applications.add(new Application(
					configObject.getString("name"),
					configObject.getString("config"),
					configObject.getString("syntax")
				));
			}
			
			for (var application: applications) {
				applicationBox.addItem(application);
			}

			showShortcuts((Application)applicationBox.getSelectedItem());
		}
		catch (IOException | JSONException e) {
			String source;

			if (e instanceof IOException) {
				source = "while accessing the config file";
			}
			else {
				source = "while parsing the config file";
			}

			shortcutsText.setText(
				printException(String.format("An error has occured %s", source), e)
			);
		}

		JFrame frame = new JFrame("Shortcuts");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		applicationBox.addActionListener( (ActionEvent event) -> {
			showShortcuts((Application)applicationBox.getSelectedItem());
		});

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.insets = new Insets(MARGIN, 0, MARGIN, 0);  // top, left, bottom, right


		mainPanel.add(applicationBox, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.insets = new Insets(0, MARGIN, MARGIN, MARGIN);
		gbc.fill = GridBagConstraints.BOTH;

		mainPanel.add(scroll, gbc);

		frame.add(mainPanel);
		frame.pack();
		frame.setVisible(true);
		frame.setLocationRelativeTo(null);

		frame.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getClassLoader().getResource("shortcuts.png")));
	}

	private void showShortcuts(Application currentApplication) {
		Path applicationConfig;
		if (
			currentApplication.getConfig().startsWith("/") || 
			(currentApplication.getConfig().length() > 1 && currentApplication.getConfig().charAt(1) == ':')
		)
			applicationConfig = Path.of(currentApplication.getConfig());
		else 
			applicationConfig = Paths.get(home, currentApplication.getConfig());

		System.out.format("Parsing configuration file at: %s\n", applicationConfig);
		Pattern pattern = Pattern.compile(currentApplication.getSyntax());

		try (Stream<String> stream = Files.lines(applicationConfig)) {
			var shortcutsBuilder = new StringBuilder();

			shortcutsBuilder.append("<table>");

			stream
				.map(
					line -> {
						Matcher matcher = pattern.matcher(line);
						if (!matcher.matches()) return Optional.<String>empty();

						return Optional.<String>of(
							String.format(
								"<tr><td><b>%s:</b></td><td><i>%s</i></td></tr>\n",
								escapeHTML(matcher.group(1)),
								escapeHTML(matcher.group(2).trim())
							)
						);
					}
				)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.forEach(shortcutsBuilder::append);

			shortcutsBuilder.append("</table>");

			shortcutsText.setText(shortcutsBuilder.toString());
			scroll.getHorizontalScrollBar().setValue(0);
			scroll.getVerticalScrollBar().setValue(0);
		}
		catch (IOException e) {
			shortcutsText.setText(
				printException("Error while parsing the application config file", e)
			);
		}

	}

	private static String printException(String message, Exception exception) {
	return
		String.format(
			"<b>%s:</b><br><i>%s</i>",
			escapeHTML(message),
			escapeHTML(exception.toString())
		);
	}

	private static String escapeHTML(String s) {
		StringBuilder builder = new StringBuilder();
		boolean previousWasASpace = false;
		for( char c : s.toCharArray() ) {
			if( c == ' ' ) {
				if( previousWasASpace ) {
					builder.append("&nbsp;");
					previousWasASpace = false;
					continue;
				}
				previousWasASpace = true;
			} else {
				previousWasASpace = false;
			}
			switch(c) {
				case '<': builder.append("&lt;"); break;
				case '>': builder.append("&gt;"); break;
				case '&': builder.append("&amp;"); break;
				case '"': builder.append("&quot;"); break;
				case '\n': builder.append("<br>"); break;
				case '\t': builder.append("&nbsp; &nbsp; &nbsp;"); break;
				default:
					if( c < 128 ) {
						builder.append(c);
					} else {
						builder.append("&#").append((int)c).append(";");
					}
			}
		}
		return builder.toString();
	}

	public static void main(String[] args) {
		if (args.length == 1 && (args[0].startsWith("-h") || args[0].startsWith("--help"))) {
			System.out.format("%s\n\nThe default config:\n\n%s\n", help, defaultConfig);
			return;
		}
		else if (args.length != 0) {
			System.out.format(
				"Unrecognized command line arguments: %s\nRun with \"-h\" or \"--help\" for help.\n",
				Arrays.toString(args)
			);
			return;
		}

		com.github.weisj.darklaf.LafManager.install(new com.github.weisj.darklaf.theme.DarculaTheme());

		Shortcuts shortcuts = new Shortcuts();
	}
}
