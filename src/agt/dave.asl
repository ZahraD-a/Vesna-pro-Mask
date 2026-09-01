// Dave is light and playful. He likes help that stays quick and does not get
// heavy.
//
// He is the one who disagrees with the other two: he does not mind being taught
// something at home, where Bob and Carol both do. That disagreement matters,
// because it means nobody in this system holds the single correct answer. Alice
// has to work out what usually goes down well, from three people who do not agree.
//
// KNOWN LIMITATION: he was also meant to be fine with a joke at work, but
// receiver.asl marks joke_deflect as out of place at work for everyone, and that
// shared rule applies to him too. So that particular difference does not currently
// exist in the code.

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
