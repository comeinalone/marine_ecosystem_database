#!/usr/bin/env python3
"""Deterministic, repeatable demonstration INSERT data for a NEW Marine_Ecosystem_Demo DB."""
from pathlib import Path
import random, datetime
random.seed(26145)
out=Path(__file__).with_name('demo_seed.sql')
def esc(s): return "'"+str(s).replace("'","''")+"'"
def sqlv(v):
    if v is None:return 'NULL'
    if isinstance(v,int):return str(v)
    return esc(v)
def insert(table, rows, columns=None, batch=75):
    if not rows:return
    for i in range(0,len(rows),batch):
        chunk=rows[i:i+batch]
        pre=f'INSERT INTO `{table}` '+('('+','.join('`'+c+'`' for c in columns)+') ' if columns else '')+'VALUES\n'
        stmts.append(pre+',\n'.join('('+', '.join(sqlv(v) for v in row)+')' for row in chunk)+';\n')

first=['Sana','Sana','Aarav','Priya','Rohan','Ananya','Meera','Ishaan','Kabir','Tara','Zoya','Dev','Neha','Arjun','Aisha','Vivaan','Riya','Aditya','Nisha','Farhan','Ira','Anika','Rahul','Pooja','Yash','Shreya','Nikhil','Rehan','Avni','Sameer']
last=['Khan','Khan','Shah','Nair','Mehta','Rao','Iyer','Sen','Kapoor','Desai','Ali','Patel','Fernandes','Verma','Sheikh','Joshi','Menon','Kulkarni','Singh','Ansari','Das','Sethi','Bose','Kamat','Naik','Pillai','Bhat','Qureshi','Gupta','Chaudhary']
specs=['Marine Biology','Genetics','Marine Ecology','Molecular Genetics','Conservation Biology','Oceanography','Fisheries Science']
researchers=[]
for i,(f,l) in enumerate(zip(first,last),1):
    specialization= 'Marine Biology' if i==1 else 'Genetics' if i==2 else specs[(i+2)%len(specs)]
    researchers.append((i,f,None,l,specialization,f'{f.lower()}.{l.lower()}{i}@example.org',f'{1980+(i*3)%19}-{1+i%12:02d}-{1+i%27:02d}'))
contacts=[(i,'Mobile',f'98{60000000+i:08d}') for i in range(1,31)]
contacts += [(i,'Work',f'0224000{i:04d}') for i in (1,2,3,4,5,10,15)]
bios=[(i,2+(i*3)%21,'Yes' if i%3 else 'No') for i in range(1,31) if researchers[i-1][4] in ('Marine Biology','Marine Ecology','Conservation Biology','Fisheries Science')]
genetics=[(i, ['Coastal Genomics Lab','Marine DNA Centre','Ocean Research Lab'][i%3], ['Population genetics','DNA barcoding','Molecular ecology'][i%3]) for i in range(1,31) if researchers[i-1][4] in ('Genetics','Molecular Genetics')]
vessels=[(101,'Ocean Explorer',24,'Mumbai'),(102,'Marine Discovery',16,'Kochi'),(103,'Blue Horizon',30,'Goa'),(104,'Coral Voyager',20,'Port Blair'),(105,'Sea Quest',12,'Mangaluru'),(106,'Nereid Surveyor',18,'Chennai'),(107,'Sagar Shakti',22,'Visakhapatnam'),(108,'Reef Guardian',14,'Kavaratti'),(109,'Tide Seeker',28,'Tuticorin'),(110,'Pelagic Star',26,'Karwar')]
regions=[(201,'Arabian Sea Station A','Arabian Sea'),(202,'Bay of Bengal Station B','Bay of Bengal'),(203,'Lakshadweep Reef','Indian Ocean'),(204,'Andaman Shelf Site 1','Bay of Bengal'),(205,'Mumbai Coastal Transect','Arabian Sea'),(206,'Goa Offshore Station','Arabian Sea'),(207,'Kochi Estuary Mouth','Arabian Sea'),(208,'Chennai Coastal Station','Bay of Bengal'),(209,'Gulf of Mannar Reef','Indian Ocean'),(210,'Nicobar Deep Station','Bay of Bengal'),(211,'Malvan Marine Reserve','Arabian Sea'),(212,'Minicoy Sampling Site','Indian Ocean')]
fish=[
('Whale Shark','Rhincodon typus','Endangered'),('Great White Shark','Carcharodon carcharias','Vulnerable'),('Yellowfin Tuna','Thunnus albacares','Least Concern'),('Scalloped Hammerhead','Sphyrna lewini','Critically Endangered'),('Giant Manta Ray','Mobula birostris','Endangered'),('Longtail Tuna','Thunnus tonggol','Least Concern'),('Indian Mackerel','Rastrelliger kanagurta','Least Concern'),('Indian Oil Sardine','Sardinella longiceps','Least Concern'),('Zebra Shark','Stegostoma tigrinum','Endangered'),('Green Sea Turtle','Chelonia mydas','Endangered'),('Clown Anemonefish','Amphiprion ocellaris','Least Concern'),('Blacktip Reef Shark','Carcharhinus melanopterus','Vulnerable'),('Hawksbill Turtle','Eretmochelys imbricata','Critically Endangered'),('Bluefin Trevally','Caranx melampygus','Least Concern'),('Reef Manta Ray','Mobula alfredi','Vulnerable'),('Tiger Shark','Galeocerdo cuvier','Near Threatened'),('Spotted Eagle Ray','Aetobatus ocellatus','Endangered'),('Barramundi','Lates calcarifer','Least Concern'),('Giant Grouper','Epinephelus lanceolatus','Data Deficient'),('Threadfin Bream','Nemipterus japonicus','Least Concern'),('Bumphead Parrotfish','Bolbometopon muricatum','Vulnerable'),('Whitetip Reef Shark','Triaenodon obesus','Vulnerable'),('Common Dolphinfish','Coryphaena hippurus','Least Concern'),('Skipjack Tuna','Katsuwonus pelamis','Least Concern'),('Bigeye Tuna','Thunnus obesus','Vulnerable'),('Silver Pomfret','Pampus argenteus','Least Concern'),('Blue Swimming Crab','Portunus pelagicus','Not Evaluated'),('Porcupinefish','Diodon holocanthus','Least Concern'),('Red Lionfish','Pterois volitans','Least Concern'),('Moorish Idol','Zanclus cornutus','Least Concern'),('Emperor Angelfish','Pomacanthus imperator','Least Concern'),('Napoleon Wrasse','Cheilinus undulatus','Endangered'),('Orange-spotted Grouper','Epinephelus coioides','Near Threatened'),('Yellowtail Scad','Atule mate','Least Concern'),('Lined Surgeonfish','Acanthurus lineatus','Least Concern'),('Blue Tang','Paracanthurus hepatus','Least Concern'),('Sailfish','Istiophorus platypterus','Least Concern'),('Swordfish','Xiphias gladius','Near Threatened'),('Olive Ridley Turtle','Lepidochelys olivacea','Vulnerable'),('Cobia','Rachycentron canadum','Least Concern'),('Shortfin Mako Shark','Isurus oxyrinchus','Endangered'),('Dugong','Dugong dugon','Vulnerable'),('Guitarfish','Glaucostegus granulatus','Critically Endangered'),('Needlefish','Tylosurus crocodilus','Least Concern'),('Milkfish','Chanos chanos','Least Concern'),('Sea Horse','Hippocampus kuda','Vulnerable'),('Mudskipper','Periophthalmus argentilineatus','Least Concern'),('Butterflyfish','Chaetodon auriga','Least Concern'),('Yellow Boxfish','Ostracion cubicus','Least Concern'),('Stonefish','Synanceia verrucosa','Least Concern')]
# Species names kept as example labels; some are non-fish marine organisms because original domain uses Fish_Species broadly.
species=[(301+i,*p) for i,p in enumerate(fish)]
# Allocate samples deliberately: prominent region and species, sufficient 1/2/3+ categories.
samples=[]
weights=[14,8,21,7,9,20,24,15,10,8]+[9]*30+[4]*10
for ix,(spid,*_) in enumerate(species):
    n=weights[ix]
    for j in range(1,n+1):
        # Species 301 Whale Shark stays concentrated in Arabian Sea Station A for predictable demo.
        if spid==301: location=201 if j<=10 else 203
        elif spid==303: location=201 if j<=11 else [202,204,205][j%3]
        elif spid==306: location=201 if j<=12 else [206,207,208][j%3]
        elif spid==308: location=202 if j<=9 else 209
        else: location=regions[(ix*5+j*3)%len(regions)][0]
        researcher=1 if spid==301 and j<=6 else 2 if spid==303 and j<=5 else 3+(ix*7+j*3)%25  # IDs 28-30 remain zero-sample
        vessel=vessels[(ix+j)%len(vessels)][0]
        day=datetime.date(2025,1,1)+datetime.timedelta(days=(ix*19+j*13)%620)
        quality='Low' if (ix*17+j)%13==0 else None if (ix*11+j)%23==0 else 'Medium' if (ix+j)%4==0 else 'High'
        marker=['COI','16S','CYTB','12S','ND2'][((ix+j)%5)]
        samples.append((spid,j,day.isoformat(),marker,quality,researcher,vessel,location))
# deliberate future-date demo and whitespace gene marker (valid under NOT NULL)
for index in [3,90,250]:
    q=list(samples[index]);q[2]='2027-03-15';samples[index]=tuple(q)
q=list(samples[137]);q[3]='   ';samples[137]=tuple(q)
stmts=['-- Fresh demonstration dataset. Synthetic records, NOT scientific observations.\n','USE `Marine_Ecosystem_Demo`;\n','START TRANSACTION;\n']
insert('Researcher',researchers);insert('Researcher_Contact',contacts);insert('Marine_Biologist',bios);insert('Geneticist',genetics);insert('Research_Vehicle',vessels);insert('Ocean_Sampling_Region',regions);insert('Fish_Species',species);insert('DNA_Sample',samples)
stmts.append('COMMIT;\n')
# After schema's audit trigger: changes generate genuine trigger audit rows, without directly writing audit table.
stmts.append('-- Produce 12 authentic trigger-generated audit entries on synthetic species.\n')
changes=[(301,'Vulnerable'),(301,'Endangered'),(304,'Endangered'),(304,'Critically Endangered'),(309,'Vulnerable'),(309,'Endangered'),(305,'Vulnerable'),(305,'Endangered'),(313,'Endangered'),(313,'Critically Endangered'),(332,'Vulnerable'),(332,'Endangered')]
for sid,status in changes:stmts.append(f'UPDATE Fish_Species SET conservation_status = {esc(status)} WHERE speciesID = {sid};\n')
stmts.append('''\n-- Quick acceptance evidence.\nSELECT 'Researchers' AS metric, COUNT(*) AS total FROM Researcher\nUNION ALL SELECT 'Species', COUNT(*) FROM Fish_Species\nUNION ALL SELECT 'DNA samples', COUNT(*) FROM DNA_Sample\nUNION ALL SELECT 'Regions', COUNT(*) FROM Ocean_Sampling_Region\nUNION ALL SELECT 'Vessels', COUNT(*) FROM Research_Vehicle\nUNION ALL SELECT 'Audit events', COUNT(*) FROM Species_Status_Audit;\n''')
out.write_text('\n'.join(stmts))
print(f'Wrote {out}; researchers {len(researchers)}, species {len(species)}, samples {len(samples)}, contacts {len(contacts)}, audit changes {len(changes)}')
