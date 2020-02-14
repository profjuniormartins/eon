#!/bin/bash 

TIMES="30000 50000"
#para links: ELEMENTS="`cat links.txt`"
ELEMENTS="`cat links.txt`"
INPUT="config_RWA_ANT_ANDRE_LinkFailure.xml"
PREFIXOUT="interdomain_ANT_1attempt"
OUTPUT_DIR=/tmpLinksANT1att
	
	for  TIME in $TIMES
	do
		for ELEMENT in $ELEMENTS
		do
			SRC="`echo $ELEMENT | cut -d- -f1`" 
			DST="`echo $ELEMENT | cut -d- -f2`"
			SRC_1=`echo $SRC | sed s/://`
			DST_1=`echo $DST | sed s/://`
			OUTPUT=$OUTPUT_DIR/link"$SRC_1"-"$DST_1"-"$TIME".xml
			#aplica substituicao
			sed s/'$src$*'/$SRC/ $INPUT > $OUTPUT.tmp
			sed s/'$dst$*'/$DST/ $OUTPUT.tmp > $OUTPUT.tmp2
			sed s/'$time$*'/$TIME/ $OUTPUT.tmp2 > $OUTPUT.tmp3
			sed s/'$out$*'/"$PREFIXOUT"_"$SRC_1"_"$DST_1"_"$TIME".txt/ $OUTPUT.tmp3 > $OUTPUT
			rm $OUTPUT.tmp*
		done	
	
	done
