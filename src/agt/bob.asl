// Bob: a busy, conscientious colleague. He appreciates being helped in a way
// that respects schedules and boundaries, and is unimpressed by anything showy.
// The strictest of the three about what belongs at work.
//
// improper/2 is his own opinion about what is out of place where. Alice cannot
// read it. She discovers it the way anyone does -- by trying it and getting a
// cold answer.

needs_help(report).

likes_style(help_after_task).
likes_style(delegate).
likes_style(teach).

// At work he wants the job done, not company.
improper(pair_up, work).
improper(joke_deflect, work).
improper(drop_everything, work).
improper(ignore, work).

// At home he finds procedure and being taught things faintly absurd.
improper(delegate, home).
improper(help_after_task, home).
improper(teach, home).

// At a conference, brushing someone off is the one thing you cannot do.
improper(polite_decline, conference).
improper(drop_everything, conference).
improper(quick_tip, conference).

{ include("receiver.asl") }
