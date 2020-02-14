#!/bin/bash 

TIMES="30000 50000"
#para links: ELEMENTS="`cat links.txt`"
ELEMENTS="`cat nodes.txt`"
INPUT="config_RWA_ANT_ANDRE_NodeFailure.xml"
PREFIXOUT="interdomain_ant_2attempt_Node"
OUTPUT_DIR=/tmpANTNode2att

for  TIME in $TIMES
do
	for ELEMENT in $ELEMENTS
	do
		NODE_OUT=`echo $ELEMENT | sed s/://`
		OUTPUT=$OUTPUT_DIR/node_"$NODE_OUT"_"$TIME".xml
		#aplica substituicao
		sed s/'$node$*'/$ELEMENT/ $INPUT > $OUTPUT.tmp 
		sed s/'$time$*'/$TIME/ $OUTPUT.tmp > $OUTPUT.tmp2
		sed s/'$out$*'/"$PREFIXOUT"_"$NODE_OUT"_"$TIME".txt/ $OUTPUT.tmp2 > $OUTPUT
		rm $OUTPUT.tmp*
	done	

done
