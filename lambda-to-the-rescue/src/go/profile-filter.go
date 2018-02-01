package _go

import (
	"bufio"
	"io"
	"strings"
)

func copy(profileName string, in io.Reader, out io.Writer) error {
	var (
		line       string
		readError  error
		writeError error
	)

	profileFilter := newProfileFilter(profileName)
	reader := bufio.NewReader(in)

	for {
		line, readError = reader.ReadString('\n')

		if profileFilter.match(line) {
			_, writeError = io.WriteString(out, line)
		}

		if readError != nil || writeError != nil {
			break
		}
	}

	 if writeError != nil {
	    return writeError
     }

     if readError != io.EOF {
        return readError
     }

     return nil
}

func newProfileFilter(profileName string) *profileFilter {
	var matchers [](func(line string) bool)

	matchers = append(matchers,
		matcher(startsWith).apply("#"+profileName),
		matcher(startsWith).apply("key2"),
		matcher(startsWith).apply("key3"),
	)

	return &profileFilter{matchers, profileName}
}

func startsWith(line string, toMatch string) bool {
	return strings.HasPrefix(
		strings.ToLower(strings.TrimSpace(line)),
		strings.ToLower(toMatch),
	)
}

type matcher func(line string, toMatch string) bool

func (f matcher) apply(toMatch string) func(line string) bool {
	return func(line string) bool {
		return f(line, toMatch)
	}
}

type profileFilter struct {
	matchers    []func(line string) bool
	profileName string
}

func (p *profileFilter) match(text string) bool {
	if len(p.matchers) == 0 {
		return false
	}

	shouldFilter := p.matchers[0](text)

	if shouldFilter {
		p.matchers = p.matchers[1:len(p.matchers)]
	}

	return shouldFilter
}