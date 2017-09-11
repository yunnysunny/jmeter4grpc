package com.whyun.jmeter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;

import com.whyun.AuthGrpc;
import com.whyun.AuthGrpc.AuthBlockingStub;
import com.whyun.AuthOuterClass;
import com.whyun.AuthOuterClass.Result;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;


public final class GrpcSampler extends AbstractJavaSamplerClient{
	/**
	 * Parameter for setting the host of grpc server
	 */
	private static final String PARAMETER_GRPC_HOST = "grpc_host";

	/**
	 * Parameter for setting the prot of grpc server.
	 */
	private static final String PARAMETER_GRPC_PORT = "grpc_port";
	
	
	private static final ThreadLocal<Integer> seqNum = new ThreadLocal<Integer>() {
		public Integer initialValue() {
			return 0;
		}
	};

	private  ManagedChannel channel;
	private  AuthBlockingStub stub;
	private String msgNo;


	private int getNextNum() {
		seqNum.set(seqNum.get() + 1);
		return seqNum.get();
	}
	
	private String getMsgNo() {
		JMeterContext contextJMeter = JMeterContextService.getContext();
		int num = getNextNum();
		int threadNum = contextJMeter.getThreadNum();
		msgNo = threadNum + "_" + num;
		Sampler sampler = contextJMeter.getCurrentSampler();
		sampler.setName("req_" + msgNo);
		return msgNo;
	}

	@Override
	public void setupTest(JavaSamplerContext context) {

		
		String host = context.getParameter(PARAMETER_GRPC_HOST);
		int port =Integer.parseInt( context.getParameter(PARAMETER_GRPC_PORT));
		ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(host, port)
		// Channels are secure by default (via SSL/TLS). For the example we
		// disable TLS to avoid
		// needing certificates.
				.usePlaintext(true);
		channel = channelBuilder.build();
		stub = AuthGrpc.newBlockingStub(channel);
 	}

	@Override
	public void teardownTest(JavaSamplerContext context) {
		try {
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Arguments getDefaultParameters() {
		Arguments defaultParameters = new Arguments();
		defaultParameters.addArgument(PARAMETER_GRPC_HOST,
				"${PARAMETER_GRPC_HOST}");
		defaultParameters.addArgument(PARAMETER_GRPC_PORT,
				"${PARAMETER_GRPC_PORT}");
		// defaultParameters.addArgument(PARAMETER_KAFKA_MESSAGE,
		// "${PARAMETER_KAFKA_MESSAGE}");

		return defaultParameters;
	}

	@Override
	public SampleResult runTest(JavaSamplerContext context) {
		SampleResult result = newSampleResult();

		getMsgNo();
		System.out.println("msgNo:" + msgNo);
		AuthOuterClass.Login.Builder builder = AuthOuterClass.Login.newBuilder();
		builder.setAvatar("https://www.google.com.hk/images/branding/googlelogo/2x/googlelogo_color_120x44dp.png")
		.setPassword("xxx")
		.setUid(msgNo)
		.setUsername(msgNo)
		.setTimestamp(new Date().getTime());
		
		sampleResultStart(result, "");

		try {			
			Result resultResp = stub.doCheck(builder.build());
			int code = resultResp.getCode();
			if (code == 0) {
				sampleResultSuccess(result, null);
			} else {
				sampleResultSuccess(result, code + "");
			}

		} catch (Exception e) {
			sampleResultFailed(result, "500", e);
		}
		return result;
	}

	/**
	 * Use UTF-8 for encoding of strings
	 */
	private static final String ENCODING = "UTF-8";

	/**
	 * Factory for creating new {@link SampleResult}s.
	 */
	private SampleResult newSampleResult() {
		SampleResult result = new SampleResult();
		result.setDataEncoding(ENCODING);
		result.setDataType(SampleResult.BINARY);
		return result;
	}

	/**
	 * Start the sample request and set the {@code samplerData} to {@code data}.
	 * 
	 * @param result
	 *            the sample result to update
	 * @param data
	 *            the request to set as {@code samplerData}
	 */
	private void sampleResultStart(SampleResult result, String data) {
		result.setSamplerData(data);
		result.sampleStart();
	}

	/**
	 * Mark the sample result as {@code end}ed and {@code successful} with an
	 * "OK" {@code responseCode}, and if the response is not {@code null} then
	 * set the {@code responseData} to {@code response}, otherwise it is marked
	 * as not requiring a response.
	 * 
	 * @param result
	 *            sample result to change
	 * @param response
	 *            the successful result message, may be null.
	 */
	private void sampleResultSuccess(SampleResult result, /* @Nullable */
			String response) {
		result.sampleEnd();
		result.setSuccessful(true);
		result.setResponseCodeOK();
		if (response != null) {
			result.setResponseData(response, ENCODING);
		} else {
			result.setResponseData("No response required", ENCODING);
		}
	}

	/**
	 * Mark the sample result as @{code end}ed and not {@code successful}, and
	 * set the {@code responseCode} to {@code reason}.
	 * 
	 * @param result
	 *            the sample result to change
	 * @param reason
	 *            the failure reason
	 */
	private void sampleResultFailed(SampleResult result, String reason) {
		result.sampleEnd();
		result.setSuccessful(false);
		result.setResponseCode(reason);
	}

	/**
	 * Mark the sample result as @{code end}ed and not {@code successful}, set
	 * the {@code responseCode} to {@code reason}, and set {@code responseData}
	 * to the stack trace.
	 * 
	 * @param result
	 *            the sample result to change
	 * @param exception
	 *            the failure exception
	 */
	private void sampleResultFailed(SampleResult result, String reason,
			Exception exception) {
		sampleResultFailed(result, reason);
		result.setResponseMessage("Exception: " + exception);
		result.setResponseData(getStackTrace(exception), ENCODING);
	}

	/**
	 * Return the stack trace as a string.
	 * 
	 * @param exception
	 *            the exception containing the stack trace
	 * @return the stack trace
	 */
	private String getStackTrace(Exception exception) {
		StringWriter stringWriter = new StringWriter();
		exception.printStackTrace(new PrintWriter(stringWriter));
		return stringWriter.toString();
	}
}
