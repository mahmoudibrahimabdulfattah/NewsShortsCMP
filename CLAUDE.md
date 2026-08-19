# News Shorts

## Commit messages

One subject line, a blank line, then bullets. No Conventional Commits
prefixes (`fix:`, `feat:`), no scopes, no trailing period on the subject.

```
Fix stale callbacks in the NewsScreen effect collector

- Wrap the four handlers in rememberUpdatedState so LaunchedEffect(Unit)
  uses the current ones, not the first composition's
- onOpenUrl carried the resolved app theme, frozen at the SYSTEM default
  before settings load — the browser toolbar was dark regardless of the
  reader's Appearance, and never changed when they switched it
```

**Subject** — under about 55 characters. Names what was fixed or added, not
which files moved.

**Bullets** — one per idea, wrapped at 76 columns, continuation lines
indented two spaces. Each one says what changed *and* what it means: the
mechanism alone (`wrap in rememberUpdatedState`) is already in the diff, so
the bullet earns its place by carrying the consequence the diff cannot show.

Be exact about symptoms. "The toolbar was dark regardless of the setting" is
findable six months later; "incorrect toolbar colors" is not.

Two to four bullets is the normal range. One is fine for a small change. If
it needs more than five, it is probably two commits.

Commits before this file are prose paragraphs instead — that was the older
convention, and they are not worth rewriting.
