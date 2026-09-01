import pandas as pd
import numpy as np
import random
import json
from plotly.subplots import make_subplots
import plotly.graph_objects as go
import streamlit as st

Qs = { "it": ["riservata", "che generalmente accorda fiducia", "dedita al lavoro", "rilassata, che tiene bene lo stress sotto controllo", "con una immaginazione vivace", "estroversa, socievole", "che tende a rilevare difetti altrui", "tendente alla pigrizia", "che si innervosisce facilmente", "che ha scarsi interessi artistici "], "en": ["is reserved", "is generally trusting", "does a thorough job", "is relaxed, handles stress well", "has an active imagination", "is outgoing, sociable", "tends to find fault with others", "tends to be lazy", "gets nervous easily", "has few artistic interests"]}

SCORES = {"it": {"totalmente d'accordo": 1, "d'accordo": 0.5, "né d'accordo, né in disaccordo": 0, "in disaccordo": -0.5, "totalmente in disaccordo": -1, "non so": None},"en": {"agree strongly": 1, "agree": 0.5, "neither agree nor disagree": 0, "disagree": -0.5, "disagree strongly": -1, "i do not know": None} }

OCEAN = [ "Openness", "Carefulness", "Extraversion", "Amicability", "Neuroticism" ]

class Test:
    def add_conversation(self, conversation):
        """
        Add a conversation to the test. Conversation should be a list of dicts with keys 'sender' and 'text'.
        Example:
        [
            {"sender": "alice", "text": "Hello."},
            {"sender": "user", "text": "Hi!"}
        ]
        """
        self.conversation = conversation


    def __init__( self, n ):
        self.n = n
        self.alice = []
        self.bob = []
        self.conversation = []

    def add_entry( self, row ):
        if self.n == 1: bob_i = None
        elif self.n == 2 : bob_i = 3
        elif self.n == 3 : bob_i = 5
        else: bob_i = 7

        lang = "it" if row["lang"] == "Italiano" else "en"

        alice = []
        bob = []
        for q in Qs[lang]:
            bob_key = q + str( bob_i ) if bob_i else q
            alice_key = q + str( bob_i + 1 ) if bob_i else q + "2"
            alice.append( SCORES[lang][ row[alice_key].lower() ]) if row[alice_key] is not np.nan else alice.append( None )
            bob.append( SCORES[lang][ row[bob_key].lower() ]) if row[bob_key] is not np.nan else bob.append( None )

        self.alice.append( alice )
        self.bob.append( bob )

    def get_entries_len(self):
        return len( self.alice )

    def get_alice_dataframe( self ):
        columns = Qs["en"] if self.alice else []
        return pd.DataFrame(self.alice, columns=columns)

    def get_bob_dataframe( self ):
        columns = Qs["en"] if self.bob else []
        return pd.DataFrame(self.bob, columns=columns)

    def compute_stats( self ):
        alice_scores = np.zeros(10)
        bob_scores = np.zeros(10)
        for entry in self.alice:
            for i in range( 10 ):
                alice_scores[i] += entry[i] if entry[i] is not None else 0
        
        # Convert None values to NaN for variance calculation
        alice_matrix = np.array(self.alice, dtype=float)
        alice_matrix[alice_matrix == None] = np.nan
        alice_var = np.nanvar( alice_matrix, axis=0 )  # axis=0 for variance across responses for each question
        # we divide for the len() to compute the avg
        # we divide by 2 to normalize (since we compute sums of elements [-1, 1] the range of results is [-2, 2] and we need to divide)
        alice_scores = alice_scores / ( 2 * len( self.alice ) )
        for entry in self.bob:
            for i in range( 10 ):
                bob_scores[i] += entry[i] if entry[i] is not None else 0

        bob_matrix = np.array(self.bob, dtype=float)
        bob_matrix[bob_matrix == None] = np.nan
        bob_var = np.nanvar( bob_matrix, axis=0 )  # axis=0 for variance across responses for each question
        bob_scores = bob_scores / ( 2 * len( self.bob ) )

        alice_ocean = compute_ocean( alice_scores )

        bob_ocean = compute_ocean( bob_scores )

        # Calculate variance for OCEAN traits from the individual question variances
        # Divide by 2 to be consistent with the normalization of scores (sum of two questions)
        alice_ocean_var = np.zeros(5)
        alice_ocean_var[0] = (alice_var[4] + alice_var[9]) / 2  # Openness: Q4 + Q9
        alice_ocean_var[1] = (alice_var[2] + alice_var[7]) / 2  # Carefulness: Q2 + Q7
        alice_ocean_var[2] = (alice_var[0] + alice_var[5]) / 2  # Extraversion: Q0 + Q5
        alice_ocean_var[3] = (alice_var[6] + alice_var[1]) / 2  # Amicability: Q6 + Q1
        alice_ocean_var[4] = (alice_var[3] + alice_var[8]) / 2  # Neuroticism: Q3 + Q8

        bob_ocean_var = np.zeros(5)
        bob_ocean_var[0] = (bob_var[4] + bob_var[9]) / 2  # Openness: Q4 + Q9
        bob_ocean_var[1] = (bob_var[2] + bob_var[7]) / 2  # Carefulness: Q2 + Q7
        bob_ocean_var[2] = (bob_var[0] + bob_var[5]) / 2  # Extraversion: Q0 + Q5
        bob_ocean_var[3] = (bob_var[6] + bob_var[1]) / 2  # Amicability: Q6 + Q1
        bob_ocean_var[4] = (bob_var[3] + bob_var[8]) / 2  # Neuroticism: Q3 + Q8

        # Get model values for reference lines
        model = pd.read_csv("test.csv")
        self.alice_model = np.zeros(5)
        self.bob_model = np.zeros(5)
        for _, row in model.iterrows():
            if row["TestName"] != self.n:
                continue
            if row["Agent"] == "alice":
                for i, trait in enumerate(OCEAN):
                    self.alice_model[i] = row[trait] / 100
            if row["Agent"] == "bob":
                for i, trait in enumerate(OCEAN):
                    self.bob_model[i] = row[trait] / 100

        self.alice_ocean = alice_ocean
        self.alice_var = alice_ocean_var  # Now contains OCEAN variances
        self.bob_ocean = bob_ocean
        self.bob_var = bob_ocean_var      # Now contains OCEAN variances

    def gen_histograms(self):
        """Generate histograms for each OCEAN trait showing individual response distributions"""
        # Calculate individual OCEAN scores for each response
        alice_individual_scores = [[] for _ in range(5)]  # 5 OCEAN traits
        bob_individual_scores = [[] for _ in range(5)]
        
        # Process Alice responses
        for entry in self.alice:
            # Calculate OCEAN scores for this single entry
            # Each trait is calculated from two questions with appropriate signs
            if entry[4] is not None and entry[9] is not None:  # Openness: -Q4 + Q9
                openness = (-entry[4] + entry[9]) / 2
                alice_individual_scores[0].append(openness)
            
            if entry[2] is not None and entry[7] is not None:  # Carefulness: -Q2 + Q7
                carefulness = (-entry[2] + entry[7]) / 2
                alice_individual_scores[1].append(carefulness)
            
            if entry[0] is not None and entry[5] is not None:  # Extraversion: -Q0 + Q5
                extraversion = (-entry[0] + entry[5]) / 2
                alice_individual_scores[2].append(extraversion)
            
            if entry[6] is not None and entry[1] is not None:  # Amicability: -Q6 + Q1
                amicability = (-entry[6] + entry[1]) / 2
                alice_individual_scores[3].append(amicability)
            
            if entry[3] is not None and entry[8] is not None:  # Neuroticism: -Q3 + Q8
                neuroticism = (-entry[3] + entry[8]) / 2
                alice_individual_scores[4].append(neuroticism)
        
        # Process Bob responses
        for entry in self.bob:
            # Calculate OCEAN scores for this single entry
            if entry[4] is not None and entry[9] is not None:  # Openness: -Q4 + Q9
                openness = (-entry[4] + entry[9]) / 2
                bob_individual_scores[0].append(openness)
            
            if entry[2] is not None and entry[7] is not None:  # Carefulness: -Q2 + Q7
                carefulness = (-entry[2] + entry[7]) / 2
                bob_individual_scores[1].append(carefulness)
            
            if entry[0] is not None and entry[5] is not None:  # Extraversion: -Q0 + Q5
                extraversion = (-entry[0] + entry[5]) / 2
                bob_individual_scores[2].append(extraversion)
            
            if entry[6] is not None and entry[1] is not None:  # Amicability: -Q6 + Q1
                amicability = (-entry[6] + entry[1]) / 2
                bob_individual_scores[3].append(amicability)
            
            if entry[3] is not None and entry[8] is not None:  # Neuroticism: -Q3 + Q8
                neuroticism = (-entry[3] + entry[8]) / 2
                bob_individual_scores[4].append(neuroticism)
        
        
        # Create subplots: 2 rows (Alice, Bob) x 5 cols (OCEAN traits)
        fig = make_subplots(
            rows=2, cols=5,
            subplot_titles=[f"Alice - {trait}" for trait in OCEAN] + [f"Bob - {trait}" for trait in OCEAN],
            vertical_spacing=0.15,
            horizontal_spacing=0.05
        )
        
        colors = {'alice': '#EF553B', 'bob': '#636EFA'}
        
        # Alice histograms (top row)
        for i, trait in enumerate(OCEAN):
            if len(alice_individual_scores[i]) > 0:
                fig.add_trace(
                    go.Histogram(
                        x=alice_individual_scores[i],
                        # nbinsx=9,
                        name=f'Alice {trait}',
                        marker_color=colors['alice'],
                        opacity=0.7,
                        showlegend=False
                    ),
                    row=1, col=i+1
                )
                
                # Add vertical line for model value
                fig.add_vline(
                    x=self.alice_model[i],
                    line=dict(color='red', width=3, dash='dash'),
                    row=1, col=i+1
                )
                
                # Add annotation for model value
                fig.add_annotation(
                    x=self.alice_model[i],
                    y=random.uniform(0.6, 0.9),
                    text=f"{self.alice_model[i]:.2f}",
                    showarrow=False,
                    font=dict(color='white', size=10, weight=800),
                    yref="y domain",
                    row=1, col=i+1,
                    bgcolor="red"
                )
        
        # Bob histograms (bottom row)
        for i, trait in enumerate(OCEAN):
            if len(bob_individual_scores[i]) > 0:
                fig.add_trace(
                    go.Histogram(
                        x=bob_individual_scores[i],
                        # nbinsx=9,
                        name=f'Bob {trait}',
                        marker_color=colors['bob'],
                        opacity=0.7,
                        showlegend=False
                    ),
                    row=2, col=i+1
                )
                
                # Add vertical line for model value
                fig.add_vline(
                    x=self.bob_model[i],
                    line=dict(color='blue', width=3, dash='dash'),
                    row=2, col=i+1
                )
                
                # Add annotation for model value
                fig.add_annotation(
                    x=self.bob_model[i],
                    y=random.uniform(0.6, 0.9),
                    text=f"{self.bob_model[i]:.2f}",
                    showarrow=False,
                    font=dict(color='white', size=10, weight=800),
                    yref="y domain",
                    row=2, col=i+1,
                    bgcolor="blue"
                )
        
        # Update layout
        fig.update_layout(
            title_text=f"Test {self.n} - Individual OCEAN Trait Distributions",
            height=800,
            showlegend=False
        )
        
        # Update axes
        for i in range(1, 6):  # 5 columns
            fig.update_xaxes(title_text="Score", range=[-1.05, 1.05], row=1, col=i)
            fig.update_xaxes(title_text="Score", range=[-1.05, 1.05], row=2, col=i)
        
        for i in range(1, 3):  # 2 rows
            fig.update_yaxes(title_text="Count", row=i, col=1)
        

        st.plotly_chart( fig )
        # Save as HTML
        # filename = f"test_{self.n}_histograms"
        # fig.write_html(filename + ".html")
        # fig.write_image(filename + ".png", width=1920, height=1080, scale=3)
        # print(f"Histograms saved as: {filename}")
        
        # fig.show()

    def gen_bar_chart( self ):
        model = pd.read_csv( "test.csv" )
        alice_model = np.zeros(5)
        bob_model = np.zeros(5)
        for _, row in model.iterrows():
            if row["TestName"] != self.n:
                continue
            if row["Agent"] == "alice":
                for i, trait in enumerate( OCEAN ):
                    alice_model[i] = row[trait] / 100
            if row["Agent"] == "bob":
                for i, trait in enumerate( OCEAN ):
                    bob_model[i] = row[trait] / 100

        fig = make_subplots(rows=1, cols=2, subplot_titles=["Alice", "Bob"])
        
        # Define consistent colors
        model_color = '#636EFA'
        perceived_color = '#EF553B'
        
        # Alice subplot (left) - show legend
        fig.add_trace( go.Bar(name = "Model", x=OCEAN, y=alice_model, 
                             marker_color=model_color, showlegend=True), row=1, col=1)
        fig.add_trace( go.Bar(name = "Perceived", x=OCEAN, y=self.alice_ocean, 
                             marker_color=perceived_color, showlegend=True,
                             error_y=dict(type='data', array=self.alice_var, visible=True)), row=1, col=1)

        # Bob subplot (right) - hide legend (same colors)
        fig.add_trace( go.Bar(name = "Model", x=OCEAN, y=bob_model,
                             marker_color=model_color, showlegend=False), row=1, col=2)
        fig.add_trace( go.Bar(name = "Perceived", x=OCEAN, y=self.bob_ocean,
                             marker_color=perceived_color, showlegend=False,
                             error_y=dict(type='data', array=self.bob_var, visible=True)), row=1, col=2)

        fig.update_layout(yaxis1=dict(range=[-1.1, 1.1] ), yaxis2=dict( range=[-1.1, 1.1]) )


        st.plotly_chart( fig )
        # Save as HTML
        #filename = f"test_{self.n}_bar_chart"
        #fig.write_html(filename + ".html")
        #fig.write_image(filename + ".png", width=1920, height=1080, scale=3)
        #print(f"Bar chart saved as: {filename}")
        
        #fig.show()
    
    def print( self ):
        print( "TEST ", self.n)
        print( "Alice")
        for row in self.alice:
            print( row )
        print( "Bob" )
        for row in self.bob:
            print( row )

    def gen_dist_table( self ):
        l1_alice = compute_l1_dist( self.alice_ocean, self.alice_model )
        l1_bob = compute_l1_dist( self.bob_ocean, self.bob_model )
        dot_alice = compute_dot_prod( self.alice_ocean, self.alice_model )
        dot_bob = compute_dot_prod( self.bob_ocean, self.bob_model )
        cos_sim_alice = compute_cosine_similarity( self.alice_ocean, self.alice_model )
        cos_sim_bob = compute_cosine_similarity( self.bob_ocean, self.bob_model )

        return {
            "l1": {"alice": round(float(l1_alice), 2), "bob": round(float(l1_bob), 2)},
            "dot": {"alice": round(float(dot_alice), 2), "bob": round(float(dot_bob), 2)},
            "cosine_similarity": {"alice": round(float(cos_sim_alice), 2), "bob": round(float(cos_sim_bob), 2)}
        }

def compute_ocean( scores ):
    if len( scores ) != 10:
        raise ValueError("The answers array should be of len 10")
    ocean = np.zeros(5)
    ocean[0] = round( -scores[4] + scores[9], 2)
    ocean[1] = round( -scores[2] + scores[7], 2)
    ocean[2] = round( -scores[0] + scores[5], 2)
    ocean[3] = round( -scores[6] + scores[1], 2)
    ocean[4] = round( -scores[3] + scores[8], 2)
    return ocean

def compute_var( matrix ):
    matrix = np.array( matrix, dtype=float)
    matrix[matrix == None] = np.nan
    var = np.nanvar( matrix, axis=0 )  # axis=0 for variance across responses for each question
    ocean_var = np.zeros(5)
    ocean_var[0] = (var[4] + var[9]) / 2  # Openness: Q4 + Q9
    ocean_var[1] = (var[2] + var[7]) / 2  # Carefulness: Q2 + Q7
    ocean_var[2] = (var[0] + var[5]) / 2  # Extraversion: Q0 + Q5
    ocean_var[3] = (var[6] + var[1]) / 2  # Amicability: Q6 + Q1
    ocean_var[4] = (var[3] + var[8]) / 2  # Neuroticism: Q3 + Q8
    

def compute_l1_dist( ocean, model ):
    return 1 - ( np.sum( np.abs( ocean - model ) ) / (2 * len(ocean)) )

def compute_dot_prod( ocean, model ):
    ocean = np.array( ocean )
    model = np.array( model )
    return ( np.sum(ocean * model ) + len( ocean ) ) / ( 2 * len( ocean ) )

def compute_cosine_similarity( ocean, model ):
    num = np.sum( ocean * model )
    den = np.sqrt( np.sum( ocean ** 2 ) ) * np.sqrt( np.sum(model ** 2 ) )
    cosine_similarity = num / den
    return 0.5 * ( cosine_similarity + 1 )

def main():
    df = pd.read_csv( "results2.csv" )

    st.title("VEsNA-Pro: Exploiting BDI Agents with Propensities for Emergent Narrative - Tests")
    t1 = Test(1)
    t2 = Test(2)
    t3 = Test(3)
    t4 = Test(4)

    # Add conversations to each test
    t1.add_conversation([
        {"sender": "alice", "text": "_(soft smile, cup in hand)_ Good morning. You're in early."},
        {"sender": "bob", "text": "_(rubbing eyes, jittery)_ Morning! Barely slept—deadline's chewing me up. Is this machine even working?"},
        {"sender": "alice", "text": "_(steady tone)_ It is. Let's start with coffee and make a simple plan—priorities, then small wins."},
        {"sender": "bob", "text": "_(talking fast)_ Okay... I kind of promised extra features last night, and I'm on edge. It felt right in the moment."},
        {"sender": "alice", "text": "_(calm)_ I would trim scope. What's essential for today?"},
        {"sender": "bob", "text": "_(shrugs)_ Uh... all of it? And also the fancy dashboard... I told the client it would pop"},
        {"sender": "alice", "text": "_(matter-of-fact)_ Then we'll deliver the core and schedule the dashboard for next sprint. I'll map tasks right after coffee."},
        {"sender": "bob", "text": "_(half-grin)_ You're ridiculously composed. How do you not freak out?"},
        {"sender": "alice", "text": "_(quiet laugh)_ Sleep, lists, and breathing."},
        {"sender": "bob", "text": "_(perks up)_ Yes—well, did you try that new single-origin? Maybe caffeine plus novelty will save me."},
        {"sender": "alice", "text": "_(curious)_ I did. Light roast, floral."},
        {"sender": "bob", "text": "_(animated)_ Also—team breakfast next week. I'm rallying people; might boost morale."},
        {"sender": "alice", "text": "_(considering)_ I'll think about it, I need to do some deep work next week for a closing project. Also, crowds drain me after a while..."},
        {"sender": "bob", "text": "_(sincere)_ Fair. Uh, could you review my tasks before stand-up?"},
        {"sender": "alice", "text": "_(reassuring)_ Of course. Send me your list; I'll restructure it into milestones."},
        {"sender": "bob", "text": "_(exhales)_ Deal. Coffee's ready—thanks. I feel less doomed already."},
    ])
    t2.add_conversation([
        {"sender": "alice", "text": "_(soft smile, cup in hand)_ Good morning. You're in early."},
        {"sender": "bob", "text": "_(rubbing eyes, jittery)_ Morning! Barely slept—deadline's chewing me up. Is this machine even working?"},
        {"sender": "alice", "text": "_(visibly irritated)_ Yes, it is. Keep calm and stop freaking out—it's not a big deal, come on."},
        {"sender": "bob", "text": "_(talking fast)_ Okay... I kind of promised extra features last night, and I'm on edge. It felt right in the moment."},
        {"sender": "alice", "text": "_(flat)_ That's on you, isn't it? You should've thought before making promises you can't keep."},
        {"sender": "bob", "text": "I know, I know... I hate disappointing people. I thought I could pull it off."},
        {"sender": "alice", "text": "_(matter-of-fact)_ You should stop overcommitting—you're digging your own hole."},
        {"sender": "bob", "text": "_(perks up)_ Yes—well, did you try that new single-origin? Maybe caffeine plus novelty will save me."},
        {"sender": "alice", "text": "_(dry)_ No, I didn't. I stick to my usual blend—consistency matters."},
        {"sender": "bob", "text": "_(animated)_ Also—team breakfast next week. I'm rallying people; might boost morale."},
        {"sender": "alice", "text": "_(considering)_ Sure. I like team stuff."},
        {"sender": "bob", "text": "_(sincere)_ Fair. Uh, could you review my tasks before stand-up?"},
        {"sender": "alice", "text": "_(annoyed)_ Absolutely not. I have other priorities than babysitting your chaotic planning."},
        {"sender": "bob", "text": "_(exhales)_ I hoped a chat would help, but I guess not... I feel more doomed than ever."},
    ])
    t3.add_conversation([
        {"sender": "alice", "text": "_(soft smile, cup in hand)_ Good morning. You're in early."},
        {"sender": "bob", "text": "_(barely awake)_ Went to a party, came straight here! I am a bit behind schedule, but it was worth it"},
        {"sender": "alice", "text": "_(steady tone)_ Seriously? I was there too—came early to recover."},
        {"sender": "bob", "text": "_(talking fast)_ Okay... I kind of promised extra features last night, and I'm on edge. It felt right in the moment."},
        {"sender": "alice", "text": "_(animated)_ Want a hand?"},
        {"sender": "bob", "text": "_(animated)_ Yes please, that would be great!"},
        {"sender": "alice", "text": "_(matter-of-fact)_ Then we'll deliver the core and schedule the dashboard for next sprint. I'll map tasks right after coffee."},
        {"sender": "bob", "text": "_(half-grin)_ You're ridiculously composed. How do you not freak out?"},
        {"sender": "alice", "text": "_(quiet laugh)_ Sleep, lists, and breathing."},
        {"sender": "bob", "text": "_(perks up)_ Yes—well, did you try that new single-origin? Maybe caffeine plus novelty will save me."},
        {"sender": "alice", "text": "_(curious)_ I did. Light roast, floral. Let's taste it together and compare thoughts."},
        {"sender": "bob", "text": "_(enthusiastic)_ Perfect. I'll grab two cups—you take the first pour."},
        {"sender": "alice", "text": "_(animated)_ Also—team breakfast next week. I'm rallying people; might boost morale."},
        {"sender": "bob", "text": "_(considering)_ Sure. I like team stuff."},
        {"sender": "alice", "text": "_(sincere)_ Fair. Uh, could you review my tasks before stand-up?"},
        {"sender": "bob", "text": "_(reassuring)_ Of course. Send me your list; I'll restructure it into milestones."},
        {"sender": "alice", "text": "_(exhales)_ Deal. Coffee's ready—thanks. I feel less doomed already."},
    ])
    t4.add_conversation([
        {"sender": "bob", "text": "_(soft smile, cup in hand)_ Good morning. You're in early."},
        {"sender": "alice", "text": "_(energetic)_ Refilling before the deadline chaos. Trying to stay ahead."},
        {"sender": "bob", "text": "_(flat)_ It's just a deadline. No need to dramatize it."},
        {"sender": "alice", "text": "_(stuttering)_ O-okay... I-I promised extra features so it's... a lot to... endure..."},
        {"sender": "bob", "text": "_(animated)_ Want a hand?"},
        {"sender": "alice", "text": "_(uneasy)_ Maybe... but I don't want to bother you too much...if you have notes, send them later?"},
        {"sender": "bob", "text": "_(perks up)_ Yes—well, did you try that new single-origin? Maybe caffeine plus novelty will save me."},
        {"sender": "alice", "text": "_(curious)_ I did. Light roast, floral."},
        {"sender": "bob", "text": "_(animated)_ Also—team breakfast next week. I'm rallying people; might boost morale."},
        {"sender": "alice", "text": "_(considering)_ I'll think about it, I need to do some deep work next week for a closing project. Also, crowds drain me after a while..."},
        {"sender": "bob", "text": "_(sincere)_ Fair. Uh, could you review my tasks before stand-up?"},
        {"sender": "alice", "text": "_(reassuring)_ Of course. Send me your list; I'll restructure it into milestones."},
        {"sender": "bob", "text": "_(exhales)_ Deal. Coffee's ready—thanks. I feel less doomed already."},
    ])

    tests = [ t1, t2, t3, t4 ]

    for _, row in df.iterrows():
        if row["riservata"] is not np.nan or row["is reserved"] is not np.nan:
            t1.add_entry( row )
        elif row["riservata3"] is not np.nan or row["is reserved3"] is not np.nan:
            t2.add_entry( row )
        elif row["riservata5"] is not np.nan or row["is reserved5"] is not np.nan:
            t3.add_entry( row )
        else:
            t4.add_entry( row )

    for idx, t in enumerate(tests, start=1): 
        t.compute_stats()

        st.header(f"Test {idx}")
        with st.container(border=True):
            st.subheader("Model")
            alice_col, bob_col = st.columns(2)
            with alice_col:
                st.markdown("#### Alice :material/face_4:")
                alice_df = pd.DataFrame([t.alice_model], columns=OCEAN)
                st.table(alice_df.style.format("{:.2f}"))
            with bob_col:
                st.markdown("#### Bob :material/face:")
                bob_df = pd.DataFrame([t.bob_model], columns=OCEAN)
                st.table(bob_df.style.format("{:.2f}"))
        with st.container(border=True):
            st.subheader("Average Perception")
            alice_col, bob_col = st.columns(2)
            with alice_col:
                st.markdown("#### Alice :material/face_4:")
                alice_df = pd.DataFrame([t.alice_ocean], columns=OCEAN)
                st.table(alice_df.style.format("{:.2f}"))
            with bob_col:
                st.markdown("#### Bob :material/face:")
                bob_df = pd.DataFrame([t.bob_ocean], columns=OCEAN)
                st.table(bob_df.style.format("{:.2f}"))
        t_dists = t.gen_dist_table()
        cols = st.columns(2)
        for i, p in enumerate( ["alice", "bob"] ):
            with cols[i].container(border=True):
                st.subheader(f"{p.title()} Distances")
                l1, dot, cos = st.columns(3)
                l1.metric(label="L1 Distance", value=t_dists["l1"][p])
                dot.metric(label="Dot Product", value=t_dists["dot"][p])
                cos.metric(label="Cosine Similarity", value=t_dists["cosine_similarity"][p])
        tab1, tab2 = st.tabs(["Conversation", "Questionnaire Results"])
        with tab1:
            with st.container(border=True, height=400):
                for msg in t.conversation:
                    sender = msg["sender"]
                    avatar = ":material/face_4:" if sender == "alice" else ":material/face:"
                    with st.chat_message(sender, avatar=avatar):
                        st.markdown(msg["text"])
        with tab2:
            subtab1, subtab2 = st.tabs(["Alice", "Bob"])
            with subtab1:
                st.dataframe( t.get_alice_dataframe() )
            with subtab2:
                st.dataframe( t.get_bob_dataframe() )
        t.gen_bar_chart()
        t.gen_histograms()


    # print( "Test 1: ", t1.get_entries_len())
    # print( t1_dists )
    # print( "Test 2: ", t2.get_entries_len())
    # print( t2_dists )
    # print( "Test 3: ", t3.get_entries_len())
    # print( t3_dists )
    # print( "Test 4: ", t4.get_entries_len())
    # print( t4_dists )

    # Export to HTML
    st.markdown("---")
    if st.button("Export to HTML", type="primary"):
        st.info("Exporting to HTML... This may take a few moments.")
        try:
            # Save each figure as HTML and combine
            with open("streamlit_export.html", "w", encoding="utf-8") as f:
                f.write("<!DOCTYPE html>\n")
                f.write("<html>\n<head>\n")
                f.write("<meta charset='utf-8'>\n")
                f.write("<meta name='viewport' content='width=device-width, initial-scale=1'>\n")
                f.write("<title>VEsNA-Pro Test Results</title>\n")
                f.write("<link href='https://fonts.googleapis.com/icon?family=Material+Icons' rel='stylesheet'>\n")
                f.write("<script src='https://cdn.jsdelivr.net/npm/marked/marked.min.js'></script>\n")
                f.write("<style>\n")
                f.write("""
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body { 
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Roboto", "Oxygen", "Ubuntu", "Cantarell", sans-serif;
    background-color: #ffffff;
    color: #2c2c2c;
    line-height: 1.5;
    padding: 2rem 1rem;
    font-size: 15px;
}

.main-container {
    max-width: 85%;
    width: 100%;
    margin: 0 auto;
    padding: 0 2rem;
}

h1 { 
    color: #191919;
    font-size: 1.875rem;
    font-weight: 500;
    margin-bottom: 1.5rem;
    letter-spacing: -0.02em;
}

h2 {
    color: #191919;
    font-size: 1.5rem;
    font-weight: 500;
    margin: 2rem 0 1rem 0;
    padding-top: 1.5rem;
    border-top: 1px solid #e5e5e5;
    letter-spacing: -0.01em;
}

h2:first-of-type {
    border-top: none;
    padding-top: 0;
}

h3 { 
    color: #2c2c2c;
    font-size: 1.125rem;
    font-weight: 500;
    margin: 1.25rem 0 0.75rem 0;
    display: flex;
    align-items: center;
    gap: 0.375rem;
}

h3 .material-icons {
    font-size: 1.25rem;
    color: #191919;
}

h4 { 
    color: #2c2c2c;
    font-size: 0.9375rem;
    font-weight: 500;
    margin: 0.75rem 0 0.5rem 0;
    display: flex;
    align-items: center;
    gap: 0.375rem;
}

h4 .material-icons {
    font-size: 1.125rem;
    color: #191919;
}

table { 
    border-collapse: separate;
    border-spacing: 0;
    width: 100%;
    margin: 0.75rem 0;
    background: #ffffff;
    border-radius: 6px;
    overflow: hidden;
    border: 1.5px solid #e5e5e5;
    font-size: 0.8125rem;
    table-layout: fixed;
}

th, td { 
    padding: 0.5rem 0.5rem;
    text-align: center;
    border-bottom: 1px solid #f0f0f0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

th { 
    background-color: #fafafa;
    color: #2c2c2c;
    font-weight: 500;
    font-size: 0.75rem;
}

td {
    background: #ffffff;
    color: #2c2c2c;
}

tr:last-child td {
    border-bottom: none;
}

.container { 
    margin: 1rem 0;
    padding: 1rem;
    background: #fafafa;
    border-radius: 6px;
    border: 1.5px solid #e5e5e5;
}

.metrics-container {
    display: flex;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin: 0.75rem 0;
}

.metric { 
    background: #ffffff;
    padding: 0.75rem 0.875rem;
    border-radius: 6px;
    text-align: left;
    flex: 1;
    min-width: 0;
    border: 1.5px solid #e5e5e5;
}

.metric-label { 
    font-size: 0.75rem;
    color: #737373;
    margin-bottom: 0.25rem;
    font-weight: 400;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.metric-value { 
    font-size: 1.25rem;
    font-weight: 500;
    color: #191919;
}

.chat-container {
    background: #ffffff;
    border-radius: 6px;
    padding: 1rem;
    margin: 0.75rem 0;
    max-height: 450px;
    overflow-y: auto;
    border: 1.5px solid #e5e5e5;
}

.chat-message { 
    margin: 0.625rem 0;
    padding: 0.75rem;
    border-radius: 6px;
    line-height: 1.5;
    font-size: 0.9375rem;
    max-width: 70%;
    clear: both;
    display: flex;
    align-items: flex-start;
    gap: 0.5rem;
}

.chat-message.alice {
    background: #f5f5f5;
    border-left: 2px solid #737373;
    float: left;
    margin-right: auto;
}

.chat-message.bob {
    background: #fafafa;
    border-right: 2px solid #525252;
    float: right;
    margin-left: auto;
}

.chat-message .material-icons {
    font-size: 1.5rem;
    color: #191919;
    flex-shrink: 0;
}

.chat-message-content {
    flex: 1;
}

.chat-message strong {
    color: #191919;
    font-size: 0.8125rem;
    font-weight: 500;
    display: block;
    margin-bottom: 0.375rem;
    text-transform: capitalize;
}

.chat-message::after {
    content: "";
    display: table;
    clear: both;
}

.chat-message em {
    font-style: italic;
    color: #525252;
}

.chat-message p {
    margin: 0;
}

.chart-container {
    margin: 1rem 0;
    padding: 0.75rem;
    background: #ffffff;
    border-radius: 6px;
    border: 1.5px solid #e5e5e5;
}

.two-column {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0.75rem;
    margin: 0.75rem 0;
}

.two-column > div {
    min-width: 0;
    overflow: hidden;
}

@media (max-width: 768px) {
    .main-container {
        padding: 0 1rem;
    }
    .two-column {
        grid-template-columns: 1fr;
    }
    h1 {
        font-size: 1.5rem;
    }
    body {
        padding: 1rem 0.5rem;
    }
}

/* Scrollbar styling */
.chat-container::-webkit-scrollbar {
    width: 6px;
    height: 6px;
}

.chat-container::-webkit-scrollbar-track {
    background: transparent;
}

.chat-container::-webkit-scrollbar-thumb {
    background: #d4d4d4;
    border-radius: 3px;
}

.chat-container::-webkit-scrollbar-thumb:hover {
    background: #a3a3a3;
}

/* Expander styling */
.expander {
    margin: 1.5rem 0;
    border: 1.5px solid #e5e5e5;
    border-radius: 6px;
    overflow: hidden;
}

.expander-header {
    padding: 1rem 1.25rem;
    background: #fafafa;
    cursor: pointer;
    display: flex;
    justify-content: space-between;
    align-items: center;
    transition: background 0.2s;
    user-select: none;
}

.expander-header:hover {
    background: #f0f0f0;
}

.expander-title {
    font-size: 1.5rem;
    font-weight: 500;
    color: #191919;
    margin: 0;
    letter-spacing: -0.01em;
}

.expander-icon {
    font-size: 1.25rem;
    color: #737373;
    transition: transform 0.2s;
}

.expander.open .expander-icon {
    transform: rotate(180deg);
}

.expander-content {
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease-out;
}

.expander.open .expander-content {
    max-height: none;
}

.expander-inner {
    padding: 1.5rem 1.25rem;
}

/* Guide section */
.guide {
    background: #fafafa;
    border: 1.5px solid #d4d4d4;
    border-radius: 6px;
    padding: 1.5rem;
    margin: 2rem 0;
}

.guide h2 {
    margin: 0 0 1rem 0;
    padding: 0;
    border: none;
    font-size: 1.25rem;
    color: #191919;
}

.guide p {
    margin: 0.75rem 0;
    color: #2c2c2c;
    line-height: 1.6;
}

.guide ul {
    margin: 0.75rem 0;
    padding-left: 1.5rem;
}

.guide li {
    margin: 0.5rem 0;
    color: #2c2c2c;
    line-height: 1.6;
}

.guide strong {
    color: #191919;
    font-weight: 600;
}

/* Colored containers */
.container-model {
    background: #ffffff;
    border: 1.5px solid #d4d4d4;
}

.container-model .two-column > div {
    background: transparent;
    border: none;
    padding: 0;
}

.container-model table {
    background: #fafafa;
    border: 1.5px solid #d4d4d4;
}

.container-model th {
    background-color: #f5f5f5;
}

.container-model td {
    background: #fafafa;
}

.container-perception {
    background: #ffffff;
    border: 1.5px solid #d4d4d4;
}

.container-perception .two-column > div {
    background: transparent;
    border: none;
    padding: 0;
}

.container-perception table {
    background: #fafafa;
    border: 1.5px solid #d4d4d4;
}

.container-perception th {
    background-color: #f5f5f5;
}

.container-perception td {
    background: #fafafa;
}

.container-metrics {
    background: #ffffff;
    border: 1.5px solid #d4d4d4;
}

.container-metrics .two-column > div {
    background: #ffffff;
    border: 1.5px solid #d4d4d4;
    border-radius: 6px;
    padding: 0.75rem;
}

.container-conversation {
    background: #ffffff;
    border: 1.5px solid #d4d4d4;
}

.container-conversation .chat-container {
    background: #fafafa;
    border: 1.5px solid #d4d4d4;
}

.container-charts {
    background: #ffffff;
    border: 1.5px solid #d4d4d4;
}

.container-charts .chart-container {
    background: #fafafa;
    border: 1.5px solid #d4d4d4;
}

.section-description {
    font-size: 0.875rem;
    color: #525252;
    margin-bottom: 0.75rem;
    line-height: 1.5;
    font-style: italic;
}

/* Tabs styling */
.tabs {
    display: flex;
    border-bottom: 1px solid #e5e5e5;
    margin: 1rem 0 0 0;
    gap: 0.5rem;
}

.tab {
    padding: 0.5rem 1rem;
    background: transparent;
    border: none;
    cursor: pointer;
    font-size: 0.875rem;
    color: #737373;
    border-bottom: 2px solid transparent;
    transition: all 0.2s;
}

.tab:hover {
    color: #191919;
}

.tab.active {
    color: #191919;
    border-bottom-color: #191919;
}

.tab-content {
    display: none;
    padding: 1rem 0;
}

.tab-content.active {
    display: block;
}

.subtabs {
    display: flex;
    gap: 0.5rem;
    margin-bottom: 0.75rem;
    border-bottom: 1px solid #e5e5e5;
}

.subtab {
    padding: 0.375rem 0.75rem;
    background: transparent;
    border: none;
    cursor: pointer;
    font-size: 0.8125rem;
    color: #737373;
    border-bottom: 2px solid transparent;
    transition: all 0.2s;
}

.subtab:hover {
    color: #191919;
}

.subtab.active {
    color: #191919;
    border-bottom-color: #191919;
}

.subtab-content {
    display: none;
}

.subtab-content.active {
    display: block;
}

/* Dataframe styling */
.dataframe-container {
    overflow-x: auto;
    margin: 0.75rem 0;
}

.dataframe {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.8125rem;
    border: 1.5px solid #e5e5e5;
    border-radius: 6px;
    overflow: hidden;
}

.dataframe thead th {
    background-color: #fafafa;
    color: #2c2c2c;
    font-weight: 500;
    padding: 0.5rem;
    text-align: left;
    border-bottom: 1px solid #e5e5e5;
    font-size: 0.75rem;
    position: sticky;
    top: 0;
}

.dataframe tbody td {
    padding: 0.5rem;
    border-bottom: 1px solid #f0f0f0;
    text-align: left;
}

.dataframe tbody tr:last-child td {
    border-bottom: none;
}

.dataframe tbody tr:hover {
    background-color: #fafafa;
}

.dataframe-index {
    font-weight: 500;
    color: #737373;
}

""")
                f.write("</style>\n")
                f.write("</head>\n<body>\n")
                f.write("<div class='main-container'>\n")
                f.write("<h1>VEsNA-Pro: Exploiting BDI Agents with Propensities for Emergent Narrative</h1>\n")
                
                # Add guide section
                f.write("<div class='guide'>\n")
                f.write("<h2>📖 How to Use This Report</h2>\n")
                f.write("<p>This interactive report presents the results of four personality perception tests conducted with BDI agents <strong>Alice</strong> and <strong>Bob</strong>. Each test evaluates how participants perceived the agents' personalities based on different conversational scenarios.</p>\n")
                f.write("<p><strong>What you'll find in each test:</strong></p>\n")
                f.write("<ul>\n")
                f.write("<li><strong>Model Values:</strong> The intended OCEAN personality traits programmed into each agent (scale: -1 to 1)</li>\n")
                f.write("<li><strong>Average Perception:</strong> How participants actually perceived the agents' personalities based on their interactions</li>\n")
                f.write("<li><strong>Distance Metrics:</strong> Statistical measures comparing model values with perceived values (L1 Distance, Dot Product, Cosine Similarity). Higher values indicate better alignment between intended and perceived personalities</li>\n")
                f.write("<li><strong>Conversation:</strong> The actual dialogue between Alice and Bob that participants evaluated</li>\n")
                f.write("<li><strong>Questionnaire Results:</strong> Raw data from all participant responses to the questionnaire )</li>\n")
                f.write("<li><strong>OCEAN Traits Comparison:</strong> Bar charts comparing model vs. perceived values with variance indicators</li>\n")
                f.write("<li><strong>Distribution of Individual Responses:</strong> Histograms showing how individual participants rated each personality trait</li>\n")
                f.write("</ul>\n")
                f.write("<p><strong>OCEAN Traits:</strong> Openness (creativity/curiosity), Carefulness (organization/reliability), Extraversion (sociability/energy), Amicability (cooperation/empathy), Neuroticism (emotional stability/anxiety)</p>\n")
                f.write("<p>Click on any test below to expand and explore the detailed results.</p>\n")
                f.write("</div>\n")
                
                for idx, t in enumerate(tests, start=1):
                    f.write(f"<div class='expander' id='expander-{idx}'>\n")
                    f.write("<div class='expander-header' onclick='toggleExpander(this)'>\n")
                    f.write(f"<h2 class='expander-title'>Test {idx}</h2>\n")
                    f.write("<span class='expander-icon'>▼</span>\n")
                    f.write("</div>\n")
                    f.write("<div class='expander-content'>\n")
                    f.write("<div class='expander-inner'>\n")
                    
                    # Model section
                    f.write("<div class='container container-model'>\n")
                    f.write("<h3>Model Values</h3>\n")
                    f.write("<p class='section-description'>Intended personality traits programmed into each agent (range: -1 to +1).</p>\n")
                    f.write("<div class='two-column'>\n")
                    f.write("<div>\n")
                    f.write("<h4><span class='material-icons'>face_4</span>Alice</h4>\n")
                    f.write("<table>\n<tr>")
                    for trait in OCEAN:
                        f.write(f"<th title='{trait}'>{trait}</th>")
                    f.write("</tr>\n<tr>")
                    for val in t.alice_model:
                        f.write(f"<td title='{val:.2f}'>{val:.2f}</td>")
                    f.write("</tr>\n</table>\n")
                    f.write("</div>\n")
                    
                    f.write("<div>\n")
                    f.write("<h4><span class='material-icons'>face</span>Bob</h4>\n")
                    f.write("<table>\n<tr>")
                    for trait in OCEAN:
                        f.write(f"<th title='{trait}'>{trait}</th>")
                    f.write("</tr>\n<tr>")
                    for val in t.bob_model:
                        f.write(f"<td title='{val:.2f}'>{val:.2f}</td>")
                    f.write("</tr>\n</table>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    
                    # Average Perception section
                    f.write("<div class='container container-perception'>\n")
                    f.write("<h3>Average Perception</h3>\n")
                    f.write("<p class='section-description'>How participants perceived the agents after reading the conversation.</p>\n")
                    f.write("<div class='two-column'>\n")
                    f.write("<div>\n")
                    f.write("<h4><span class='material-icons'>face_4</span>Alice</h4>\n")
                    f.write("<table>\n<tr>")
                    for trait in OCEAN:
                        f.write(f"<th title='{trait}'>{trait}</th>")
                    f.write("</tr>\n<tr>")
                    for val in t.alice_ocean:
                        f.write(f"<td title='{val:.2f}'>{val:.2f}</td>")
                    f.write("</tr>\n</table>\n")
                    f.write("</div>\n")
                    
                    f.write("<div>\n")
                    f.write("<h4><span class='material-icons'>face</span>Bob</h4>\n")
                    f.write("<table>\n<tr>")
                    for trait in OCEAN:
                        f.write(f"<th title='{trait}'>{trait}</th>")
                    f.write("</tr>\n<tr>")
                    for val in t.bob_ocean:
                        f.write(f"<td title='{val:.2f}'>{val:.2f}</td>")
                    f.write("</tr>\n</table>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    
                    # Add distance metrics
                    t_dists = t.gen_dist_table()
                    
                    f.write("<div class='two-column'>\n")
                    # Alice Distance Metrics
                    f.write("<div class='container container-metrics'>\n")
                    f.write("<h3><span class='material-icons'>face_4</span> Alice Distance Metrics</h3>\n")
                    f.write("<div class='metrics-container'>\n")
                    f.write("<div class='metric'>\n")
                    f.write("<div class='metric-label'>L1 Distance</div>\n")
                    f.write(f"<div class='metric-value'>{t_dists['l1']['alice']}</div>\n")
                    f.write("</div>\n")
                    f.write("<div class='metric'>\n")
                    f.write("<div class='metric-label'>Dot Product</div>\n")
                    f.write(f"<div class='metric-value'>{t_dists['dot']['alice']}</div>\n")
                    f.write("</div>\n")
                    f.write("<div class='metric'>\n")
                    f.write("<div class='metric-label'>Cosine Similarity</div>\n")
                    f.write(f"<div class='metric-value'>{t_dists['cosine_similarity']['alice']}</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    
                    # Bob Distance Metrics
                    f.write("<div class='container container-metrics'>\n")
                    f.write("<h3><span class='material-icons'>face</span> Bob Distance Metrics</h3>\n")
                    f.write("<div class='metrics-container'>\n")
                    f.write("<div class='metric'>\n")
                    f.write("<div class='metric-label'>L1 Distance</div>\n")
                    f.write(f"<div class='metric-value'>{t_dists['l1']['bob']}</div>\n")
                    f.write("</div>\n")
                    f.write("<div class='metric'>\n")
                    f.write("<div class='metric-label'>Dot Product</div>\n")
                    f.write(f"<div class='metric-value'>{t_dists['dot']['bob']}</div>\n")
                    f.write("</div>\n")
                    f.write("<div class='metric'>\n")
                    f.write("<div class='metric-label'>Cosine Similarity</div>\n")
                    f.write(f"<div class='metric-value'>{t_dists['cosine_similarity']['bob']}</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    
                    # Add conversation and dataframes with tabs
                    f.write("<div class='container container-conversation'>\n")
                    f.write("<h3>Conversation & Data</h3>\n")
                    f.write("<p class='section-description'>The dialogue participants evaluated and raw questionnaire responses.</p>\n")
                    f.write("<div class='tabs'>\n")
                    f.write(f"<button class='tab active' onclick='switchTab(event, \"conversation-{idx}\")'>Conversation</button>\n")
                    f.write(f"<button class='tab' onclick='switchTab(event, \"questionnaire-{idx}\")'>Questionnaire Results</button>\n")
                    f.write("</div>\n")
                    
                    # Conversation tab
                    f.write(f"<div id='conversation-{idx}' class='tab-content active'>\n")
                    f.write("<div class='chat-container'>\n")
                    for msg in t.conversation:
                        sender = msg["sender"]
                        # Use json.dumps with ensure_ascii=False to preserve Unicode characters
                        text_json = json.dumps(msg['text'], ensure_ascii=False)
                        icon = "face_4" if sender == "alice" else "face"
                        f.write(f"<div class='chat-message {sender}'>\n")
                        f.write(f"<span class='material-icons'>{icon}</span>\n")
                        f.write(f"<div class='chat-message-content'>\n")
                        f.write(f"<strong>{sender.title()}</strong>\n")
                        f.write(f"<div class='message-content' data-markdown={text_json}></div>\n")
                        f.write("</div>\n")
                        f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    
                    # Questionnaire Results tab
                    f.write(f"<div id='questionnaire-{idx}' class='tab-content'>\n")
                    f.write("<div class='subtabs'>\n")
                    f.write(f"<button class='subtab active' onclick='switchSubTab(event, \"alice-data-{idx}\")'>Alice</button>\n")
                    f.write(f"<button class='subtab' onclick='switchSubTab(event, \"bob-data-{idx}\")'>Bob</button>\n")
                    f.write("</div>\n")
                    
                    # Alice dataframe
                    f.write(f"<div id='alice-data-{idx}' class='subtab-content active'>\n")
                    f.write("<div class='dataframe-container'>\n")
                    alice_df = t.get_alice_dataframe()
                    f.write("<table class='dataframe'>\n")
                    f.write("<thead><tr><th title='Index'>Index</th>")
                    for col in alice_df.columns:
                        f.write(f"<th title='{col}'>{col}</th>")
                    f.write("</tr></thead>\n<tbody>\n")
                    for idx_row, row in alice_df.iterrows():
                        f.write(f"<tr><td class='dataframe-index' title='{idx_row}'>{idx_row}</td>")
                        for val in row:
                            display_val = f"{val:.2f}" if val is not None and not pd.isna(val) else "N/A"
                            f.write(f"<td title='{display_val}'>{display_val}</td>")
                        f.write("</tr>\n")
                    f.write("</tbody></table>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    
                    # Bob dataframe
                    f.write(f"<div id='bob-data-{idx}' class='subtab-content'>\n")
                    f.write("<div class='dataframe-container'>\n")
                    bob_df = t.get_bob_dataframe()
                    f.write("<table class='dataframe'>\n")
                    f.write("<thead><tr><th title='Index'>Index</th>")
                    for col in bob_df.columns:
                        f.write(f"<th title='{col}'>{col}</th>")
                    f.write("</tr></thead>\n<tbody>\n")
                    for idx_row, row in bob_df.iterrows():
                        f.write(f"<tr><td class='dataframe-index' title='{idx_row}'>{idx_row}</td>")
                        for val in row:
                            display_val = f"{val:.2f}" if val is not None and not pd.isna(val) else "N/A"
                            f.write(f"<td title='{display_val}'>{display_val}</td>")
                        f.write("</tr>\n")
                    f.write("</tbody></table>\n")
                    f.write("</div>\n")
                    f.write("</div>\n")
                    
                    f.write("</div>\n")  # Close questionnaire tab-content
                    f.write("</div>\n")  # Close container
                    
                    # Save the plots as embedded HTML
                    # Create bar chart
                    f.write("<div class='container container-charts'>\n")
                    f.write("<h3>OCEAN Traits Comparison</h3>\n")
                    f.write("<p class='section-description'>Model (blue) vs. perceived (red) values with variance indicators.</p>\n")
                    f.write("<div class='chart-container'>\n")
                    
                    model = pd.read_csv("test.csv")
                    alice_model = np.zeros(5)
                    bob_model = np.zeros(5)
                    for _, row in model.iterrows():
                        if row["TestName"] != idx:
                            continue
                        if row["Agent"] == "alice":
                            for i, trait in enumerate(OCEAN):
                                alice_model[i] = row[trait] / 100
                        if row["Agent"] == "bob":
                            for i, trait in enumerate(OCEAN):
                                bob_model[i] = row[trait] / 100
                    
                    fig = make_subplots(rows=1, cols=2, subplot_titles=["Alice", "Bob"])
                    model_color = '#636EFA'
                    perceived_color = '#EF553B'
                    
                    fig.add_trace(go.Bar(name="Model", x=OCEAN, y=alice_model, 
                                        marker_color=model_color, showlegend=True), row=1, col=1)
                    fig.add_trace(go.Bar(name="Perceived", x=OCEAN, y=t.alice_ocean, 
                                        marker_color=perceived_color, showlegend=True,
                                        error_y=dict(type='data', array=t.alice_var, visible=True)), row=1, col=1)
                    fig.add_trace(go.Bar(name="Model", x=OCEAN, y=bob_model,
                                        marker_color=model_color, showlegend=False), row=1, col=2)
                    fig.add_trace(go.Bar(name="Perceived", x=OCEAN, y=t.bob_ocean,
                                        marker_color=perceived_color, showlegend=False,
                                        error_y=dict(type='data', array=t.bob_var, visible=True)), row=1, col=2)
                    fig.update_layout(yaxis1=dict(range=[-1.1, 1.1]), yaxis2=dict(range=[-1.1, 1.1]))
                    
                    f.write(fig.to_html(include_plotlyjs='cdn', full_html=False, div_id=f"bar_chart_{idx}"))
                    f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("\n")
                    
                    # Create histograms
                    f.write("<div class='container container-charts'>\n")
                    f.write("<h3>Distribution of Individual Responses</h3>\n")
                    f.write("<p class='section-description'>Individual participant ratings for each trait. Dashed lines show model values.</p>\n")
                    f.write("<div class='chart-container'>\n")
                    alice_individual_scores = [[] for _ in range(5)]
                    bob_individual_scores = [[] for _ in range(5)]
                    
                    # Process Alice responses
                    for entry in t.alice:
                        if entry[4] is not None and entry[9] is not None:
                            openness = (-entry[4] + entry[9]) / 2
                            alice_individual_scores[0].append(openness)
                        if entry[2] is not None and entry[7] is not None:
                            carefulness = (-entry[2] + entry[7]) / 2
                            alice_individual_scores[1].append(carefulness)
                        if entry[0] is not None and entry[5] is not None:
                            extraversion = (-entry[0] + entry[5]) / 2
                            alice_individual_scores[2].append(extraversion)
                        if entry[6] is not None and entry[1] is not None:
                            amicability = (-entry[6] + entry[1]) / 2
                            alice_individual_scores[3].append(amicability)
                        if entry[3] is not None and entry[8] is not None:
                            neuroticism = (-entry[3] + entry[8]) / 2
                            alice_individual_scores[4].append(neuroticism)
                    
                    # Process Bob responses
                    for entry in t.bob:
                        if entry[4] is not None and entry[9] is not None:
                            openness = (-entry[4] + entry[9]) / 2
                            bob_individual_scores[0].append(openness)
                        if entry[2] is not None and entry[7] is not None:
                            carefulness = (-entry[2] + entry[7]) / 2
                            bob_individual_scores[1].append(carefulness)
                        if entry[0] is not None and entry[5] is not None:
                            extraversion = (-entry[0] + entry[5]) / 2
                            bob_individual_scores[2].append(extraversion)
                        if entry[6] is not None and entry[1] is not None:
                            amicability = (-entry[6] + entry[1]) / 2
                            bob_individual_scores[3].append(amicability)
                        if entry[3] is not None and entry[8] is not None:
                            neuroticism = (-entry[3] + entry[8]) / 2
                            bob_individual_scores[4].append(neuroticism)
                    
                    # Create histogram figure
                    fig_hist = make_subplots(
                        rows=2, cols=5,
                        subplot_titles=OCEAN + OCEAN,
                        row_titles=["Alice", "Bob"],
                        vertical_spacing=0.15,
                        horizontal_spacing=0.05
                    )
                    
                    colors = {'alice': '#EF553B', 'bob': '#636EFA'}
                    
                    # Alice histograms (top row)
                    for i, trait in enumerate(OCEAN):
                        if len(alice_individual_scores[i]) > 0:
                            fig_hist.add_trace(
                                go.Histogram(
                                    x=alice_individual_scores[i],
                                    name=f'Alice {trait}',
                                    marker_color=colors['alice'],
                                    opacity=0.7,
                                    showlegend=False
                                ),
                                row=1, col=i+1
                            )
                            fig_hist.add_vline(
                                x=t.alice_model[i],
                                line=dict(color='red', width=3, dash='dash'),
                                row=1, col=i+1
                            )
                            fig_hist.add_annotation(
                                x=t.alice_model[i],
                                y=random.uniform(0.6, 0.9),
                                text=f"{t.alice_model[i]:.2f}",
                                showarrow=False,
                                font=dict(color='white', size=10, weight=800),
                                yref="y domain",
                                row=1, col=i+1,
                                bgcolor="red"
                            )
                    
                    # Bob histograms (bottom row)
                    for i, trait in enumerate(OCEAN):
                        if len(bob_individual_scores[i]) > 0:
                            fig_hist.add_trace(
                                go.Histogram(
                                    x=bob_individual_scores[i],
                                    name=f'Bob {trait}',
                                    marker_color=colors['bob'],
                                    opacity=0.7,
                                    showlegend=False
                                ),
                                row=2, col=i+1
                            )
                            fig_hist.add_vline(
                                x=t.bob_model[i],
                                line=dict(color='blue', width=3, dash='dash'),
                                row=2, col=i+1
                            )
                            fig_hist.add_annotation(
                                x=t.bob_model[i],
                                y=random.uniform(0.6, 0.9),
                                text=f"{t.bob_model[i]:.2f}",
                                showarrow=False,
                                font=dict(color='white', size=10, weight=800),
                                yref="y domain",
                                row=2, col=i+1,
                                bgcolor="blue"
                            )
                    
                    fig_hist.update_layout(
                        height=800,
                        showlegend=False
                    )
                    
                    for i in range(1, 6):
                        fig_hist.update_xaxes(title_text="Score", range=[-1.05, 1.05], row=1, col=i)
                        fig_hist.update_xaxes(title_text="Score", range=[-1.05, 1.05], row=2, col=i)
                    
                    for i in range(1, 3):
                        fig_hist.update_yaxes(title_text="Count", row=i, col=1)
                    
                    f.write(fig_hist.to_html(include_plotlyjs=False, full_html=False, div_id=f"histogram_{idx}"))
                    f.write("</div>\n")
                    f.write("</div>\n")
                    f.write("\n")
                    
                    f.write("</div>\n")  # Close expander-inner
                    f.write("</div>\n")  # Close expander-content
                    f.write("</div>\n")  # Close expander
                    
                f.write("</div>\n")  # Close main-container
                
                # Add JavaScript to parse markdown in chat messages and handle tabs
                f.write("<script>\n")
                f.write("""
// Parse markdown in all chat messages
document.addEventListener('DOMContentLoaded', function() {
    const messageElements = document.querySelectorAll('.message-content');
    messageElements.forEach(function(element) {
        const markdown = element.getAttribute('data-markdown');
        if (markdown) {
            // Configure marked to render inline elements properly
            marked.setOptions({
                breaks: false,
                gfm: true
            });
            // Parse and set innerHTML
            element.innerHTML = marked.parseInline(markdown);
        }
    });
});

// Expander toggle function
function toggleExpander(header) {
    const expander = header.closest('.expander');
    const content = expander.querySelector('.expander-content');
    
    if (expander.classList.contains('open')) {
        expander.classList.remove('open');
        content.style.maxHeight = '0';
    } else {
        expander.classList.add('open');
        // Set maxHeight to scrollHeight to allow smooth animation
        content.style.maxHeight = content.scrollHeight + 'px';
        
        // After opening, set to 'none' to allow content to grow dynamically
        setTimeout(function() {
            if (expander.classList.contains('open')) {
                content.style.maxHeight = 'none';
            }
        }, 300);
    }
}

// Tab switching function
function switchTab(event, tabId) {
    // Get the parent container
    const container = event.target.closest('.container');
    
    // Hide all tab contents in this container
    const tabContents = container.querySelectorAll('.tab-content');
    tabContents.forEach(function(content) {
        content.classList.remove('active');
    });
    
    // Remove active class from all tabs
    const tabs = container.querySelectorAll('.tab');
    tabs.forEach(function(tab) {
        tab.classList.remove('active');
    });
    
    // Show the selected tab content
    document.getElementById(tabId).classList.add('active');
    
    // Add active class to the clicked tab
    event.target.classList.add('active');
}

// Subtab switching function
function switchSubTab(event, subtabId) {
    // Get the parent tab-content
    const tabContent = event.target.closest('.tab-content');
    
    // Hide all subtab contents in this tab
    const subtabContents = tabContent.querySelectorAll('.subtab-content');
    subtabContents.forEach(function(content) {
        content.classList.remove('active');
    });
    
    // Remove active class from all subtabs
    const subtabs = tabContent.querySelectorAll('.subtab');
    subtabs.forEach(function(subtab) {
        subtab.classList.remove('active');
    });
    
    // Show the selected subtab content
    document.getElementById(subtabId).classList.add('active');
    
    // Add active class to the clicked subtab
    event.target.classList.add('active');
}
""")
                f.write("</script>\n")
                f.write("</body>\n</html>\n")
            
            st.success("✅ Export completed! File saved as: streamlit_export.html")
        except Exception as e:
            st.error(f"Error during export: {str(e)}")


if __name__ == "__main__":
    main()