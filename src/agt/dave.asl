// Dave: playful and light. He likes help that stays quick and unserious.
//
// He is where the disagreement lives: unlike Bob and Carol he sees nothing
// wrong with a joke at work, and unlike them he does not mind being taught
// something at home. So there is no oracle anywhere in this system -- only
// three people who partly disagree, which Alice has to average over experience.

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
