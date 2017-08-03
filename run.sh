#!/bin/sh

rm start_ignite.o*
rm start_ignite.e*
rm run_firewall_ignite.o*
rm run_firewall_ignite.e*

nodes=`python readJsonField.py configuration.json nodes`    # Number of nodes
ppn=`python readJsonField.py configuration.json ppn`        # Processors per node

# Run ignite nodes
for id in `seq 1 $nodes`; 
do 
    qsub start_ignite.sub -l nodes=1:ppn=$ppn; 
done

sleep 5 # wait for ignite initialization

# run main job
qsub run_firewall_ignite.sub
