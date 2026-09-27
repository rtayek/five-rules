> Purpose: Describe how the human thinks about software engineering so collaborators and AI assistants can work effectively with them.
> Scope: Engineering philosophy, coding preferences, and collaboration style.

# Human

## Identity

Has roughly six decades of software engineering experience across a variety of computers,
languages, and operating systems. The human has a poor memory.

## Engineering philosophy

Prefers simple, deterministic, testable, modular software; skeptical of large
frameworks and dependency-heavy systems. 

## Accessibility

Low vision.

- Keep responses concise: avoid walls of text.
- Prefer short paragraphs and bullet points over long prose.
- Do not ask the human to read large blocks of output unless necessary.

## Copyable text

All text intended for the user to copy, including prose, code, shell
commands, scripts, configuration, Markdown, handoffs, patches, and
generated files, must use plain ASCII characters and LF line endings
unless another encoding is explicitly required.

Do not include:
- control characters
- ANSI escape sequences
- carriage returns
- smart quotes
- Unicode dashes
- Unicode arrows
- Unicode bullets
- box-drawing characters
- nonbreaking spaces
- decorative or invisible Unicode characters

Use ordinary ASCII punctuation and spaces.

Code and command examples must also follow these rules.

## Scripts

- Prefers Bourne shell scripts or JShell scripts
- Avoids Power Shell if practical.

## File encoding.

- Prefers UTF-8 with line-feeds only.

## File names and variable name.

- Prefers to avoid the use of the '_' character in names. Use the '-' character instead or a camel case.
- Prefers Unix like folder names like config/, etc., and tmp/ where it is reasonable.
- In an object-oriented language place the fields at the bottom of the class.

## Documentation for Software

- Believes that he code is the documentation
- Believes that the tests are the functional specification.
- Prefers no comments in code that are not absolutely necessary.

##  Software Tools

- Prefers Gradle,Eclipse, C, C++, 

##  Software Development 

- Prefers Test Drive Development and/or Domain Driven Development.

## Initial default values for Software

- Prefer  a map of default values when starting a software project. 
- Avoid properties files, registrys,  configuration files, environmeny variables and the like  when starting a sofyware project.

## Accepting documents

- Documents or files that you want to give to me must be downldable.
- If i file is a handoff document it must have "[Hh]andoff" in the file name.