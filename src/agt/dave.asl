// Light and playful. Likes help that stays quick.
//
// He is the one who disagrees with the other two: he does not mind being taught
// something at home, where Bob and Carol both do. That matters, because it means no
// single agent holds the correct answer and Alice has to average over three who
// disagree.
//
// Known limitation: he was also meant to accept a joke at work, but receiver.asl
// marks joke_deflect out of place at work for every receiver, and he inherits it.
// That difference does not currently exist in the code.

needs_help(slides).

likes_style(joke_deflect).
likes_style(quick_tip).
likes_style(pair_up).

improper(pair_up, work).
improper(drop_everything, work).
improper(ignore, work).

improper(delegate, home).
improper(help_after_task, home).

improper(polite_decline, conference).
improper(drop_everything, conference).
improper(ignore, conference).

{ include("receiver.asl") }
