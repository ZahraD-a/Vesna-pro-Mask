// Carol cares about people being present. What works with her is someone actually
// stopping and being there. She minds coldness much more than she minds someone
// being informal, and at home she finds being handled by the book insulting.

needs_help(setup).

likes_style(drop_everything).
likes_style(pair_up).
likes_style(teach).

improper(pair_up, work).
improper(joke_deflect, work).
improper(ignore, work).

improper(delegate, home).
improper(help_after_task, home).
improper(teach, home).
improper(polite_decline, home).

improper(polite_decline, conference).
improper(quick_tip, conference).
improper(ignore, conference).

{ include("receiver.asl") }
