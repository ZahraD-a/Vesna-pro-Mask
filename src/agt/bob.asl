// Bob is busy and organised. He likes being helped in a way that respects his
// schedule, and is not impressed by anything showy. Of the three he is the
// strictest about what belongs at work.
//
// improper/2 below is his own opinion about what is out of place where. Alice
// cannot read it; she only finds out by trying something and getting a cold
// reply.

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
