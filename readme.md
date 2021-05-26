# Shortcuts

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
		- Form: `(?:regex)`

The config file's location is: `$HOME/.config/shortcuts/shortcuts.conf`.
If it is not found there then the directory is created and a default config
file is placed there.

The default configuration:

```json
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
			"syntax": "nnoremap ([a-zA-Z0-9<>,-]+) (.*)"
		}
	]
}

```

The application can be run with the command line argument `-h` or `--help` to get this help
printed to the standard output.
