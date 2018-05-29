##!/usr/bin/perl
# compare_versions.pl
# Purpose: compare two runs/version of Jerboa
# usage: compare_versions version1 version2
# Version 2.0 P.R. Rijnbeek
# Version 2.1 MG - fixed bug: result was set to 1 even if same number of lines in both files
# Version 2.2 MG - modified the file retrieving to go through subfolders also
# Version 2.3 MG - modified the sort to remove numeric parts of the file names and then sort

use strict;
use warnings;
use File::Find;

my $dir1 = $ARGV[0];
my $dir2 = $ARGV[1];

my $datestring = gmtime();

#Get all the files in the two result directories
opendir(DIR, $dir1);
#my @files1 = sort readdir(DIR) or die $!;

#new - get files from subfolders also
my @files1;
find( \&filesFirstDir, $dir1);
#@files1 = sort @files1;

@files1 = map $_->[0],
  sort { $a->[1] cmp $b->[1] }
  map [ $_, tr|0-9||dr ], @files1;

closedir(DIR);


opendir(DIR, $dir2);
#my @files2 = sort readdir(DIR) or die $!;

#new - get files from subfolders also
my @files2;
find( \&filesSecondDir, $dir2);
#@files2 = sort @files2;

@files2 = map $_->[0],
  sort { $a->[1] cmp $b->[1] }
  map [ $_, tr|0-9||dr ], @files2;

closedir(DIR);

my $result = 0;

print $datestring, "\nComparison of $dir1 with $dir2:\n";

my $nrfiles1 = @files1;
my $nrfiles2 = @files2;

if ($nrfiles1 != $nrfiles2) {
   print "Different number of result files!!\n";
   print "$dir1:\n";
   printDir($dir1);
   print "$dir2:\n";
   printDir($dir2);
   
   $result = 1;
} else {
   for (my $i=0; $i<$nrfiles1;$i++){

  	 if (($files1[$i] =~ m/.*.txt/) | ($files1[$i] =~ m/.*.csv/)){
 			#print "<", $dir1, $files1[$i], "\n";
			#print ">", $dir2, $files2[$i], "\n";
			#compare_files("${dir1}$files1[$i]","${dir2}$files2[$i]");	   
			#print "-------------------------------------------------\n";
			
			#new. with files from subfolders also
			print "<", $files1[$i], "\n";
			print ">", $files2[$i], "\n";
			compare_files("$files1[$i]","$files2[$i]");	   
			print "-------------------------------------------------\n";  
   
  	 }
   }
}
	
exit $result;

sub compare_files {
	my $file1 = $_[0];
	my $file2 = $_[1];
	
	open( FILE1, "< $file1" ) or die "Can not read file $file1: $! \n";
	my @file1_contents = <FILE1>;    # read entire contents of file
	close(FILE1);
	open( FILE2, "< $file2" ) or die "Can not read file $file2: $! \n";
	my @file2_contents = <FILE2>;    # read entire contents of file
	close(FILE2);
	my $length1 = $#file1_contents;    # number of lines in first file
	my $length2 = $#file2_contents;    # number of lines in second file

	my $identical = 1;
	if ( $length1 > $length2 ) {
		$result = 1;
		# first file contains more lines than second file
	    my $counter2 = 0;
	    foreach my $line_file1 (@file1_contents) {
		chomp($line_file1);
		if ( defined( $file2_contents[$counter2] ) ) {

		    # line exists in second file
		    chomp( my $line_file2 = $file2_contents[$counter2] );
		    if ( $line_file1 ne $line_file2 ) {
			print "\nline " . ( $counter2 + 1 ) . " \n";
			print "< $line_file1 \n" if ( $line_file1 ne "" );
			print "--- \n";
			print "> $line_file2 \n\n" if ( $line_file2 ne "" );
			$identical = 0;
		    }
		}
		else {

		    # there is no line in second file
		    print "\nline " . ( $counter2 + 1 ) . " \n";
		    print "< $line_file1 \n" if ( $line_file1 ne "" );
		    print "--- \n";
		    print "> \n";    # this line does not exist in file2
		    $identical = 0;
		}
		$counter2++;         # point to the next line in file2
	    }
	}
	else {
	    # second file contains more lines than first file
	    # or both have equal number of lines
	    my $counter1 = 0;
	    foreach my $line_file2 (@file2_contents) {
		chomp($line_file2);
		if ( defined( $file1_contents[$counter1] ) ) {

		    # line exists in first file
		    chomp( my $line_file1 = $file1_contents[$counter1] );
		    if ( $line_file1 ne $line_file2 ) {
			print "\nline " . ( $counter1 + 1 ) . " \n";
			print "< $line_file1 \n" if ( $line_file1 ne "" );
			print "--- \n";
			print "> $line_file2 \n" if ( $line_file2 ne "" );
			$identical = 0;
			$result = 1;
		    }
		}
		else {

		    # there is no line in first file
		    print "\nline " . ( $counter1 + 1 ) . " \n";
		    print "< \n";    # this line does not exist in file1
		    print "--- \n";
		    print "> $line_file2 \n" if ( $line_file2 ne "" );
		    $identical = 0;
		    $result = 1;
		}
		$counter1++;         # point to next line in file1
	    }
	}

	if ($identical) {
	  print "Files are identical!\n";
	}  
}

sub printDir {
	my $dir = $_[0];
	opendir(DIR, $dir1);
	while ( my $file = readdir(DIR)) {
 		 print "$file\n";
	}
	closedir(DIR);
}

sub filesFirstDir {
	push @files1, $File::Find::name;
	return;
}

sub filesSecondDir {
	push @files2, $File::Find::name;
	return;
}