package example.substance;

import gsrs.module.substance.utils.ImageInfo;
import gsrs.module.substance.utils.ImageUtilities;
import ix.ginas.modelBuilders.SubstanceBuilder;
import ix.ginas.models.v1.Name;
import ix.ginas.models.v1.Reference;
import ix.ginas.models.v1.Substance;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

public class ImageUtilitiesTest {

    @Test
    public void testSubstanceWithoutImage(){
        SubstanceBuilder builder = new SubstanceBuilder();
        Name plainName = new Name();
        plainName.name="Plain Substance";
        plainName.displayName=true;

        Reference reference = new Reference();
        reference.publicDomain= true;
        reference.docType="book";
        reference.citation="Descriptions of stuff, page 203";
        reference.addTag("PUBLIC_DOMAIN_RELEASE");
        plainName.addReference(reference);
        builder.addName(plainName);
        builder.addReference(reference);
        Substance substance = builder.build();
        ImageUtilities imageUtilities = new ImageUtilities();
        ImageInfo imageInfo = imageUtilities.getSubstanceImage(substance);
        Assertions.assertFalse(imageInfo.isHasData());
    }

    @Test
    public void testSubstanceWithImage() {
        SubstanceBuilder builder = new SubstanceBuilder();
        Name plainName = new Name();
        plainName.name="Plain Substance";
        plainName.displayName=true;

        Reference reference = new Reference();
        reference.publicDomain= true;
        reference.docType="book";
        reference.citation="Descriptions of stuff, page 203";
        reference.addTag("PUBLIC_DOMAIN_RELEASE");
        reference.addTag(ImageUtilities.SUBSTANCE_IMAGE_TAG);
        reference.uploadedFile="https://upload.wikimedia.org/wikipedia/commons/1/1d/Feldspar-Group-291254.jpg";
        plainName.addReference(reference);
        builder.addName(plainName);
        builder.addReference(reference);
        Substance substance = builder.build();
        ImageUtilities imageUtilities = new ImageUtilities();
        ImageInfo imageInfo= imageUtilities.getSubstanceImage(substance);
        Assertions.assertTrue(imageInfo.isHasData() && imageInfo.getImageData().length>0);
    }

    @Test
    public void resizeImageTest1() {
        String imageUrl ="https://upload.wikimedia.org/wikipedia/commons/1/1d/Feldspar-Group-291254.jpg";
        try {
            URL fileUrl = new URL(imageUrl);
            InputStream is = fileUrl.openStream ();
            byte[] imageBytes = IOUtils.toByteArray(is);
            byte[] resizedBytes= ImageUtilities.resizeImage(imageBytes, 50, 50, "jpg");
            File basicFile = new File("d:\\temp\\del2Resized.jpg");
            assert resizedBytes != null;
            Files.write(basicFile.toPath(), resizedBytes);
            Assertions.assertTrue(resizedBytes.length>0);
        }
        catch (IOException e) {
            System.err.printf ("Failed while reading bytes from %s: %s", imageUrl, e.getMessage());
            e.printStackTrace ();
            Assertions.fail("error processing image fails test");
        }
    }

    @Test
    public void resizeImageTest2() {
        String imagePath ="testImage/puppy1.png";
        try {
            File imageFile = new ClassPathResource(imagePath).getFile();
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            byte[] resizedBytes= ImageUtilities.resizeImage(imageBytes, 200, 100, "png");
            File basicFile = new File("d:\\temp\\delResizedPuppy2.png");
            assert resizedBytes != null;
            Files.write(basicFile.toPath(), resizedBytes);
            Assertions.assertTrue(resizedBytes.length>0);
        }
        catch (IOException e) {
            System.err.printf ("Failed while reading bytes from %s: %s", imagePath, e.getMessage());
            e.printStackTrace ();
            Assertions.fail("error processing image fails test");
        }
    }

    @Test
    public void resizeImageTest3() {
        String imageUrl ="https://upload.wikimedia.org/wikipedia/commons/1/1d/Feldspar-Group-291254.jpg";
        try {
            URL fileUrl = new URL(imageUrl);
            InputStream is = fileUrl.openStream ();
            byte[] imageBytes = IOUtils.toByteArray(is);
            byte[] resizedBytes= ImageUtilities.resizeImage(imageBytes, 50, 50, "jpeg");
            File basicFile = new File("d:\\temp\\del2Resized.jpg");
            assert resizedBytes != null;
            Files.write(basicFile.toPath(), resizedBytes);
            Assertions.assertTrue(resizedBytes.length>0);
        }
        catch (IOException e) {
            System.err.printf ("Failed while reading bytes from %s: %s", imageUrl, e.getMessage());
            e.printStackTrace ();
            Assertions.fail("error processing image fails test");
        }
    }

    @Test
    public void resizeImageTest4() {
        String imagePath ="testImage/pentagon.svg";
        try {
            File imageFile = new ClassPathResource(imagePath).getFile();
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            byte[] resizedBytes= ImageUtilities.resizeImage(imageBytes, 200, 100, "svg");
            File basicFile = new File("d:\\temp\\delResizedPentagon.svg");
            assert resizedBytes != null;
            Files.write(basicFile.toPath(), resizedBytes);
            Assertions.assertTrue(resizedBytes.length>0);
        }
        catch (IOException e) {
            System.err.printf ("Failed while reading bytes from %s: %s", imagePath, e.getMessage());
            e.printStackTrace ();
            Assertions.fail("error processing image fails test");
        }
    }

    @Test
    public void resizeImageTest5() {
        String imagePath ="testImage/simple_x.tiff";
        try {
            File imageFile = new ClassPathResource(imagePath).getFile();
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            byte[] resizedBytes= ImageUtilities.resizeImage(imageBytes, 200, 100, "tif");
            File basicFile = new File("d:\\temp\\delResizedSimpleX.tiff");
            assert resizedBytes != null;
            Files.write(basicFile.toPath(), resizedBytes);
            Assertions.assertTrue(resizedBytes.length>0);
        }
        catch (IOException e) {
            System.err.printf ("Failed while reading bytes from %s: %s", imagePath, e.getMessage());
            e.printStackTrace ();
            Assertions.fail("error processing image fails test");
        }
    }
//
}
