#!/bin/bash
#usage sh ./run_and_compare.sh
#Version 2.0 - author Mees Mosseveld

echo - Declare all locations:
jar_folder="./target/"
workspace="./workspace"
echo "  " jar_folder ""= $jar_folder
echo "  " workspace " "= $workspace

echo - Create workspace folder and comparison subfolder
mkdir workspace
cd workspace
mkdir comparison
cd ..

echo - Unzip data to workspace folder
echo "  unzip" ./Resources/Data/Patients_anonymized.zip
unzip -qq ./Resources/Data/Patients_anonymized.zip -d ./workspace
echo "  unzip" ./Resources/Data/Events_anonymized.zip
unzip -qq ./Resources/Data/Events_anonymized.zip -d ./workspace
echo "  unzip" ./Resources/Data/Measurements_anonymized.zip
unzip -qq ./Resources/Data/Measurements_anonymized.zip -d ./workspace
echo "  unzip" ./Resources/Data/Prescriptions_anonymized.zip
unzip -qq ./Resources/Data/Prescriptions_anonymized.zip -d ./workspace

echo - Unzip reference to workspace folder
unzip -qq ./Resources/Reference/Results.zip -d ./workspace

echo - Copy .jsf files to workspace folder
cp ./Resources/*.jsf ./workspace

echo - Get the latest jar
jarfile=$(find $jar_folder -name '*jar-with-dependencies.jar')
echo "  " jarfile = $jarfile

echo - Launch it and save exit status
script_file="Travis_settings.jsf"
echo "  " script_file = $script_file
echo "  " java -Xmx4G -jar $jarfile $workspace $script_file IPCI
java -Xmx4G -jar $jarfile $workspace $script_file IPCI

echo - Launch comparison only if the jar launched and ran successfully
if [ $? -eq 0 ]; then
	#get latest folder and the reference folder
	cd "$workspace/jerboa"
	latest=$(ls -td -- * | head -n 1)
	reference="$workspace/results"

	#get a time stamp for the results file name
	timestamp=$(date +%s)

	#create the output file name
	output_file="$workspace/comparison/comparison_$latest_$timestamp.log"
	
	# Move up two folders
	cd ../..
	
	#echo - List reference set
	#ls -lR $reference
	
	#echo - List result set
	#ls -lR $workspace/jerboa/$latest/results 

	echo - Compare result set with reference set
	perl "./scripts/compare_versions.pl" "$workspace/jerboa/$latest/results/" "$reference/" > $output_file
else
	exit 1
fi
head -n100 $output_file

# --- TO BE FINISHED -----IF NEEDED #
#comparison based on check sums also for .pdf (if generated - not currently in CLI)

#find "$workspace/jerboa/$latest/results/" -regex ".*\.\(pdf\|txt\|csv\)" | sort | xargs cksum
#find "$workspace/jerboa/$latest/results/" -name "*.pdf" | sort | xargs cksum > log
#find "$workspace/$reference/results/" -name "*.pdf" | sort | xargs cksum > log

#diff <(find "$workspace/jerboa/$latest/results" | sort | xargs cksum) <(find "$reference/#results" | sort | xargs cksum)