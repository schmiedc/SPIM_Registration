package spim.fiji.datasetmanager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;

import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.Modulo;
import loci.formats.meta.MetadataRetrieve;
import mpicbg.spim.io.IOFunctions;
import ome.xml.model.primitives.PositiveFloat;
import spim.fiji.spimdata.imgloaders.LightSheetZ1ImgLoader;

public class LightSheetZ1MetaData
{
	private String objective = "";
	private String calUnit = "um";
	private int rotationAxis = -1;
	private String channels[];
	private String angles[];
	private String illuminations[];
	private int numT = -1;
	private int numI = -1;
	private double calX, calY, calZ, lightsheetThickness = -1;
	private String[] files;
	private HashMap< Integer, int[] > imageSizes;
	private int pixelType = -1;
	private int bytesPerPixel = -1; 
	private String pixelTypeString = "";
	private boolean isLittleEndian;

	public void setRotationAxis( final int rotAxis ) { this.rotationAxis = rotAxis; }
	public void setCalX( final double calX ) { this.calX = calX; }
	public void setCalY( final double calY ) { this.calY = calY; }
	public void setCalZ( final double calZ ) { this.calZ = calZ; }
	public void setCalUnit( final String calUnit ) { this.calUnit = calUnit; }

	public int numChannels() { return channels.length; }
	public int numAngles() { return angles.length; }
	public int numIlluminations() { return numI; }
	public int numTimepoints() { return numT; }
	public String objective() { return objective; }
	public int rotationAxis() { return rotationAxis; }
	public double calX() { return calX; }
	public double calY() { return calY; }
	public double calZ() { return calZ; }
	public String[] files() { return files; }
	public String[] channels() { return channels; }
	public String[] angles() { return angles; }
	public String[] illuminations() { return illuminations; }
	public HashMap< Integer, int[] > imageSizes() { return imageSizes; }
	public String calUnit() { return calUnit; }
	public double lightsheetThickness() { return lightsheetThickness; }
	public int pixelType() { return pixelType; }
	public int bytesPerPixel() { return bytesPerPixel; }
	public String pixelTypeString() { return pixelTypeString; }
	public boolean isLittleEndian() { return isLittleEndian; }
	public String rotationAxisName()
	{
		if ( rotationAxis == 0 )
			return "X";
		else if ( rotationAxis == 1 )
			return "Y";
		else if ( rotationAxis == 2 )
			return "Z";
		else
			return "Unknown";
	}

	public boolean loadMetaData( final File cziFile )
	{
		final IFormatReader r = LightSheetZ1ImgLoader.instantiateImageReader();

		if ( !LightSheetZ1ImgLoader.createOMEXMLMetadata( r ) )
		{
			try { r.close(); } catch (IOException e) { e.printStackTrace(); }
			IOFunctions.println( "Creating MetaDataStore failed. Stopping" );
			return false;
		}

		try
		{
			r.setId( cziFile.getAbsolutePath() );

			this.pixelType = r.getPixelType();
			this.bytesPerPixel = FormatTools.getBytesPerPixel( pixelType ); 
			this.pixelTypeString = FormatTools.getPixelTypeString( pixelType );
			this.isLittleEndian = r.isLittleEndian();

			if ( !( pixelType == FormatTools.UINT8 || pixelType == FormatTools.UINT16 || pixelType == FormatTools.UINT32 || pixelType == FormatTools.FLOAT ) )
			{
				IOFunctions.println(
						"LightSheetZ1MetaData.loadMetaData(): PixelType " + pixelTypeString +
						" not supported yet. Please send me an email about this: stephan.preibisch@gmx.de - stopping." );

				r.close();

				return false;
			}

			//printMetaData( r );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "File '" + cziFile.getAbsolutePath() + "' could not be opened: " + e );
			IOFunctions.println( "Stopping" );

			e.printStackTrace();
			try { r.close(); } catch (IOException e1) { e1.printStackTrace(); }
			return false;
		}

		final Hashtable< String, Object > metaData = r.getGlobalMetadata();
		final int numA = r.getSeriesCount();

		// make sure every angle has the same amount of timepoints, channels, illuminations
		this.numT = -1;
		this.numI = -1;
		int numC = -1;
		
		// also collect the image sizes for each angle
		this.imageSizes = new HashMap< Integer, int[] >();

		try
		{
			for ( int a = 0; a < numA; ++a )
			{
				r.setSeries( a );

				int w = r.getSizeX();
				int h = r.getSizeY();
				int d = (int)Math.round( Double.parseDouble( metaData.get( "Information|Image|V|View|SizeZ #" + (a+1) ).toString() ) );

				imageSizes.put( a, new int[]{ w, h, d } );

				if ( numT >= 0 && numT != r.getSizeT() )
				{
					IOFunctions.println( "Number of timepoints inconsistent across angles. Stopping." );
					r.close();
					return false;
				}
				else
				{
					numT = r.getSizeT();
				}
				
				// Illuminations are contained within the channel count; to
				// find the number of illuminations for the current angle:
				Modulo moduloC = r.getModuloC();

				if ( numI >= 0 && numI != moduloC.length() )
				{
					IOFunctions.println( "Number of illumination directions inconsistent across angles. Stopping." );
					r.close();
					return false;
				}
				else
				{
					numI = moduloC.length();
				}

				if ( numC >= 0 && numC != r.getSizeC() / moduloC.length() )
				{
					IOFunctions.println( "Number of channels directions inconsistent across angles. Stopping." );
					r.close();
					return false;
				}
				else
				{
					numC = r.getSizeC() / moduloC.length();
				}
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the main meta data: " + e + "\n. Stopping." );
			try { r.close(); } catch (IOException e1) { e1.printStackTrace(); }
			return false;
		}

		//
		// query non-essential details
		//
		this.channels = new String[ numC ];
		this.angles = new String[ numA ];
		this.illuminations = new String[ numI ];
		this.files = r.getSeriesUsedFiles();

		for ( int i = 0; i < numI; ++i )
			illuminations[ i ] = String.valueOf( i );

		Object tmp;

		try
		{
			tmp = metaData.get( "Experiment|AcquisitionBlock|AcquisitionModeSetup|Objective #1" );
			objective = (tmp != null) ? tmp.toString() : "Unknown Objective";

			for ( int c = 0; c < numC; ++c )
			{
				tmp = metaData.get( "Information|Image|Channel|Wavelength #" + ( c+1 ) );
				// round and cast back to String
				channels[ c ] = (tmp != null) ? String.valueOf( (int)Math.round( Double.parseDouble( tmp.toString() ) ) ) : String.valueOf( c );
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the objective used: " + e + "\n. Proceeding." );
			for ( int c = 0; c < numC; ++c )
				channels[ c ] = String.valueOf( c );
		}

		try
		{
			boolean allAnglesNegative = true;
			final int[] anglesTmp = new int[ numA ];

			for ( int a = 0; a < numA; ++a )
			{
				tmp = metaData.get( "Information|Image|V|View|Offset #" + ( a+1 ) );
				anglesTmp[ a ] = (tmp != null) ? (int)Math.round( Double.parseDouble( tmp.toString() ) ) : a;

				if ( anglesTmp[ a ] > 0 )
					allAnglesNegative = false;
			}

			if ( allAnglesNegative )
				for ( int a = 0; a < numA; ++a )
					anglesTmp[ a ] *= -1;

			for ( int a = 0; a < numA; ++a )
				angles[ a ] = String.valueOf( anglesTmp[ a ] );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the rotation angles: " + e + "\n. Proceeding." );
			for ( int a = 0; a < numA; ++a )
				angles[ a ] = String.valueOf( a );
		}

		try
		{
			tmp = metaData.get( "Information|Image|V|AxisOfRotation #1" );
			if ( tmp != null && tmp.toString().trim().length() == 5 )
			{
				final String[] axes = tmp.toString().split( " " );
				
				if ( Integer.parseInt( axes[ 0 ] ) == 1 )
					rotationAxis = 0;
				else if ( Integer.parseInt( axes[ 1 ] ) == 1 )
					rotationAxis = 1;
				else if ( Integer.parseInt( axes[ 2 ] ) == 1 )
					rotationAxis = 2;
				else
					rotationAxis = -1;
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the rotation axis: " + e + "\n. Proceeding." );
			rotationAxis = -1;
		}

		try
		{
			for ( final String key : metaData.keySet() )
			{
				if ( key.startsWith( "LsmTag|Name #" ) && metaData.get( key ).toString().trim().equals( "LightSheetThickness" ) )
				{
					String lookup = "LsmTag " + key.substring( key.indexOf( '#' ), key.length() );
					tmp = metaData.get( lookup );

					if ( tmp != null )
						lightsheetThickness = Double.parseDouble( tmp.toString() );
					else
						lightsheetThickness = -1;
				}
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the lightsheet thickness: " + e + "\n. Proceeding." );
			lightsheetThickness = -1;
		}

		try
		{
			final MetadataRetrieve retrieve = (MetadataRetrieve)r.getMetadataStore();

			float cal = 0;

			PositiveFloat f = retrieve.getPixelsPhysicalSizeX( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension X seems corrupted, setting to 1." );
			}
			calX = cal;

			f = retrieve.getPixelsPhysicalSizeY( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension Y seems corrupted, setting to 1." );
			}
			calY = cal;

			f = retrieve.getPixelsPhysicalSizeZ( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension Z seems corrupted, setting to 1." );
			}
			calZ = cal;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the calibration: " + e + "\n. Proceeding." );
			calX = calY = calZ = 1;
		};

		try { r.close(); } catch (IOException e) { e.printStackTrace(); }

		return true;
	}

	public static void printMetaData( final IFormatReader r )
	{
		final Hashtable< String, Object > metaData = r.getGlobalMetadata();

		ArrayList< String > entries = new ArrayList<String>();

		for ( final String s : metaData.keySet() )
			entries.add( "'" + s + "': " + metaData.get( s ) );

		Collections.sort( entries );

		for ( final String s : entries )
			System.out.println( s );
	}
}
